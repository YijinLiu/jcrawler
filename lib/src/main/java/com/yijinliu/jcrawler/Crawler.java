package com.yijinliu.jcrawler;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;
import java.util.concurrent.locks.ReentrantLock;

import com.google.common.flogger.FluentLogger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public class Crawler {

    public static String sanitizeFilename(String filename) {
        return filename.replace('/', '_').replace(':', '_');
    }

    public Crawler(int numThreads, String downloadRoot, String logFile) {
        this.executor = Executors.newFixedThreadPool(numThreads);
        this.downloadRoot = downloadRoot;
        this.handlers = new ArrayList<Handler>();
        this.phaser = new Phaser();
        this.phaser.register();
        this.lock = new ReentrantLock();
        this.urlToCrawls = new HashMap<>();
        this.urlToDownloads = new HashMap<>();
        this.failedCrawls = new ConcurrentLinkedDeque<>();
        this.failedDownloads = new ConcurrentLinkedDeque<>();
        if (!logFile.isEmpty()) {
            try {
                this.logWriter = new PrintWriter(new FileWriter(logFile, true), true);
            } catch (IOException e) {
                logger.atWarning().withCause(e).log("Failed to open log file '%s'.", logFile);
            }
        }
    }

    public void addHandler(Handler handler) {
        handlers.add(handler);
        logger.atInfo().log("Added handler '%s'.", handler.name());
    }

    public boolean crawl(String url, int timeoutMillis, int maxTries) {
        url = sanitizeUrl(url);
        if (!tryIncVal(url, 0, urlToCrawls)) return false;
        enqueueCrawl(url, timeoutMillis, maxTries);
        return true;
    }

    public boolean retryCrawl(String url, int timeoutMillis, int maxTries) {
        url = sanitizeUrl(url);
        if (!tryIncVal(url, maxTries, urlToCrawls)) return false;
        enqueueCrawl(url, timeoutMillis, maxTries);
        return true;
    }
    
    public boolean download(String url, String filename, String referer, String cookies,
                            int timeoutMillis, int maxTries) {
        url = sanitizeUrl(url);
        if (!tryIncVal(url, 0, urlToDownloads)) return false;
        enqueueDownload(url, filename, referer, cookies, timeoutMillis, maxTries);
        return true;
    }
    
    public boolean retryDownload(String url, String filename, String referer, String cookies,
                                 int timeoutMillis, int maxTries) {
        url = sanitizeUrl(url);
        if (!tryIncVal(url, maxTries, urlToDownloads)) return false;
        enqueueDownload(url, filename, referer, cookies, timeoutMillis, maxTries);
        return true;
    }

    public void shutdown() {
        logger.atWarning().log("Waiting for all jobs to complete ...");
        phaser.arriveAndAwaitAdvance();
        logger.atInfo().log("Successfully crawled %d URLs.", crawledUrls());
        if (!failedCrawls.isEmpty()) {
            logger.atWarning().log("Failed to crawl %d URLs:", failedCrawls.size());
            Iterator<String> it = failedCrawls.iterator();
            while (it.hasNext()) logger.atWarning().log("\t" + it.next());
        }
        logger.atInfo().log("Successfully downloaded %d files.", downloadedFiles());
        if (!failedDownloads.isEmpty()) {
            logger.atWarning().log("Failed to download %d files:", failedDownloads.size());
            Iterator<String> it = failedDownloads.iterator();
            while (it.hasNext()) logger.atWarning().log("\t" + it.next());
        }
        executor.shutdown();
        if (this.logWriter != null) {
            this.logWriter.close();
        }
    }

    private void crawlUrl(String url, int timeoutMillis, int maxTries) {
        logger.atInfo().log("Crawling '%s'...", url);
        try {
            Document doc = Jsoup.parse(new URL(url), timeoutMillis);
            for (Handler handler : handlers) {
                if (handler.Handle(url, doc, this)) {
                    logger.atFine().log("[%s] Handled '%s'.", handler.name(), url);
                    return;
                }
            }
        } catch (MalformedURLException e) {
            logger.atWarning().withCause(e).log("Invalid URL '%s'.", url);
        } catch (IOException e) {
            logger.atWarning().withCause(e).log("Failed to crawl '%s'.", url);
            if (!retryCrawl(url, timeoutMillis, maxTries)) {
                logger.atWarning().log("Max tries reached for '%s'.", url);
                failedCrawls.add(url);
            }
        }
    }

    private void downloadUrl(String url, String filename, String referer, String cookies,
                             int timeoutMillis, int maxTries) {
        Path path = Paths.get(downloadRoot, filename);
        try {
            path.getParent().toFile().mkdirs();

            while (true) {
                logger.atInfo().log("Downloading '%s'...", url);
                URL urlObj = new URL(url);
                HttpURLConnection conn = (HttpURLConnection)urlObj.openConnection();
                conn.setReadTimeout(timeoutMillis);
                conn.addRequestProperty("Host", urlObj.getHost());
                conn.addRequestProperty("User-Agent", "Wget/1.19.4 (linux-gnu)");
                conn.addRequestProperty("Accept", "*/*");
                conn.addRequestProperty("Accept-Encoding", "identity");
                if (!referer.isEmpty()) conn.addRequestProperty("Referer", referer);
                if (!cookies.isEmpty()) conn.addRequestProperty("Cookie", cookies);
                switch (conn.getResponseCode()) {
                    case HttpURLConnection.HTTP_OK:
                        Files.copy(
                            conn.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);
                        if (logWriter != null) logDownloadedFile(url, filename);
                        return;
                    case HttpURLConnection.HTTP_MOVED_TEMP:
                    case HttpURLConnection.HTTP_MOVED_PERM:
                    case HttpURLConnection.HTTP_SEE_OTHER:
                        referer = url;
                        url = sanitizeUrl(conn.getHeaderField("Location"));
                        String newCookies = conn.getHeaderField("Set-Cookie");
                        if (!newCookies.isEmpty()) cookies = newCookies;
                        break;
                    case HttpURLConnection.HTTP_NOT_FOUND:
                        logger.atWarning().log("Not found '%s'.", url);
                        failedDownloads.add(url);
                        return;
                    case HttpURLConnection.HTTP_BAD_REQUEST:
                        logger.atWarning().log("Bad request '%s': %s", url, conn.getResponseMessage());
                        failedDownloads.add(url);
                        return;
                    default:
                        throw new IOException(conn.getResponseMessage());
                }
            }
        } catch (MalformedURLException e) {
            logger.atWarning().withCause(e).log("Invalid download URL '%s'.", url);
        } catch (IOException e) {
            logger.atWarning().withCause(e).log("Failed to download '%s'.", url);
            if (!retryDownload(url, filename, referer, cookies, timeoutMillis, maxTries)) {
                logger.atWarning().log("Max tries reached for '%s'.", url);
                failedDownloads.add(url);
            }
        }
    }

    public void enqueueCrawl(String url, int timeoutMillis, int maxTries) {
        phaser.register();
        executor.execute(() -> {
            crawlUrl(url, timeoutMillis, maxTries);
            phaser.arrive();
        });
        logger.atInfo().log("Queued URL '%s'.", url);
    }
    
    public void enqueueDownload(String url, String filename, String referer, String cookies,
                                int timeoutMillis, int maxTries) {
        phaser.register();
        executor.execute(() -> {
            downloadUrl(url, filename, referer, cookies, timeoutMillis, maxTries);
            phaser.arrive();
        });
        logger.atInfo().log("Queued URL '%s'(%s).", url, filename);
    }

    private boolean tryIncVal(String key, int maxVal, HashMap<String, Integer> map) {
        lock.lock();
        Integer val = map.putIfAbsent(key, 1);
        if (val != null) {
            if (val >= maxVal) {
                lock.unlock();
                return false;
            }
            map.put(key, val + 1);
        }
        lock.unlock();
        return true;
    }

    private int crawledUrls() {
        int crawls = 0;
        lock.lock();
        crawls = urlToCrawls.size();
        lock.unlock();
        return crawls - failedCrawls.size();
    }

    private int downloadedFiles() {
        int downloads = 0;
        lock.lock();
        downloads = urlToDownloads.size();
        lock.unlock();
        return downloads - failedDownloads.size();
    }

    private synchronized void logDownloadedFile(String url, String file) {
        logWriter.println(DownloadedFile.builder().setUrl(url).setFile(file).build().toJson());
    }

    private static String responseBody(HttpURLConnection conn) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
            int ch = 0;
            while ((ch = reader.read()) != -1) builder.append((char)ch);
        }
        return builder.toString();
    }

    private static String sanitizeUrl(String url) {
        // TODO: Find a decent way to do this.
        return url.replaceAll(" ", "%20");
    }
    
    private ExecutorService executor;
    private String downloadRoot;
    private ArrayList<Handler> handlers;
    private Phaser phaser;
    private ReentrantLock lock;
    private HashMap<String, Integer> urlToCrawls;
    private HashMap<String, Integer> urlToDownloads;
    private ConcurrentLinkedDeque<String> failedCrawls;
    private ConcurrentLinkedDeque<String> failedDownloads;
    private ArrayList<DownloadedFile> downloadedFiles;
    private PrintWriter logWriter;

    private static final FluentLogger logger = FluentLogger.forEnclosingClass();
}
