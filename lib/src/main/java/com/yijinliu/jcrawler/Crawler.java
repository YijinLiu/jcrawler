package com.yijinliu.jcrawler;

import java.io.IOException;
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

    public Crawler(int numThreads, String downloadRoot) {
        this.executor = Executors.newFixedThreadPool(numThreads);
        this.downloadRoot = downloadRoot;
        this.handlers = new ArrayList<Handler>();
        this.phaser = new Phaser();
        this.phaser.register();
        this.lock = new ReentrantLock();
        this.urlToCrawls = new HashMap();
        this.urlToDownloads = new HashMap();
        this.failedCrawls = new ConcurrentLinkedDeque();
        this.failedDownloads = new ConcurrentLinkedDeque();
    }

    public void addHandler(Handler handler) {
        handlers.add(handler);
        logger.atInfo().log("Added handler '%s'.", handler.name());
    }

    public boolean crawl(String url, int timeoutMillis, int maxTries) {
        if (!tryIncVal(url, 0, urlToCrawls)) return false;
        enqueueCrawl(url, timeoutMillis, maxTries);
        return true;
    }

    public boolean retryCrawl(String url, int timeoutMillis, int maxTries) {
        if (!tryIncVal(url, maxTries, urlToCrawls)) return false;
        enqueueCrawl(url, timeoutMillis, maxTries);
        return true;
    }
    
    public boolean download(String url, String filename, int maxTries) {
        if (!tryIncVal(url, 0, urlToDownloads)) return false;
        enqueueDownload(url, filename, maxTries);
        return true;
    }
    
    public boolean retryDownload(String url, String filename, int maxTries) {
        if (!tryIncVal(url, maxTries, urlToDownloads)) return false;
        enqueueDownload(url, filename, maxTries);
        return true;
    }

    public void shutdown() {
        logger.atWarning().log("Waiting for all jobs to complete ...");
        phaser.arriveAndAwaitAdvance();
        if (!failedCrawls.isEmpty()) {
            logger.atWarning().log("Failed to crawl %d URLs:", failedCrawls.size());
            Iterator<String> it = failedCrawls.iterator();
            while (it.hasNext()) logger.atWarning().log("\t" + it.next());
        }
        if (!failedDownloads.isEmpty()) {
            logger.atWarning().log("Failed to download %d files:", failedDownloads.size());
            Iterator<String> it = failedDownloads.iterator();
            while (it.hasNext()) logger.atWarning().log("\t" + it.next());
        }
        executor.shutdown();
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

    private void downloadUrl(String url, String filename, int maxTries) {
        logger.atInfo().log("Downloading '%s'...", url);
        Path path = Paths.get(downloadRoot, filename);
        try {
            Files.copy(new URL(url).openStream(), path, StandardCopyOption.REPLACE_EXISTING);
        } catch (MalformedURLException e) {
            logger.atWarning().withCause(e).log("Invalid download URL '%s'.", url);
        } catch (IOException e) {
            logger.atWarning().withCause(e).log("Failed to download '%s'.", url);
            if (!retryDownload(url, filename, maxTries)) {
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
    
    public void enqueueDownload(String url, String filename, int maxTries) {
        phaser.register();
        executor.execute(() -> {
            downloadUrl(url, filename, maxTries);
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
    
    private ExecutorService executor;
    private String downloadRoot;
    private ArrayList<Handler> handlers;
    private Phaser phaser;
    private ReentrantLock lock;
    private HashMap<String, Integer> urlToCrawls;
    private HashMap<String, Integer> urlToDownloads;
    private ConcurrentLinkedDeque<String> failedCrawls;
    private ConcurrentLinkedDeque<String> failedDownloads;

    private static final FluentLogger logger = FluentLogger.forEnclosingClass();
}
