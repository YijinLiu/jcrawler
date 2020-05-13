package com.yijinliu.jcrawler;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.flogger.FluentLogger;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class PdfdriveHandler implements Handler {

    public final static String CATEGORY_URL_PREFIX = "https://www.pdfdrive.com/category/";
    
    public final static String SEARCH_URL_PREFIX = "https://www.pdfdrive.com/search?q=";
    
    public final static Pattern tagUrlPattern = Pattern.compile(
        "https://www.pdfdrive.com/[-0-9a-f]+-books.html");

    public final static Pattern bookUrlPattern = Pattern.compile(".+-e[0-9]+[.]html");

    public final static Pattern onClickPattern = Pattern.compile(
        "initConverter[(]'([0-9]+)','([0-9a-f]+)','EPUB'[)];.*");

    public final static int TIMEOUT_MILLIS = 30000;

    public final static int MAX_TRIES = 2;

    public String name() {
        return "springer";
    }

    @Override
    public boolean Handle(String url, Document doc, Crawler crawler) {
        if (url.startsWith(CATEGORY_URL_PREFIX) || url.startsWith(SEARCH_URL_PREFIX) ||
                tagUrlPattern.matcher(url).matches()) {
            doc.select(".files-new a").forEach((el) -> {
                String bookUrl = el.absUrl("href");
                if (bookUrlPattern.matcher(bookUrl).matches()) {
                    crawler.crawl(bookUrl, TIMEOUT_MILLIS, MAX_TRIES);
                } else {
                    logger.atWarning().log("Unknown URL '%s'.", bookUrl);
                }
            });
            doc.select(".pagination li > a").forEach((el) -> {
                if (el.className().isEmpty()) {
                    crawler.crawl(el.absUrl("href"), TIMEOUT_MILLIS, MAX_TRIES);
                }
            });
            return true;
        } else if (bookUrlPattern.matcher(url).matches()) {
            Element titleEl = doc.selectFirst(".ebook-main h1");
            if (titleEl == null) {
                logger.atWarning().log("Failed to find title element for '%s'.", url);
                if (!crawler.retryCrawl(url, TIMEOUT_MILLIS, MAX_TRIES)) {
                    logger.atWarning().log("Too many failures on '%s', won't retry.", url);
                }
                return true;
            }
            Elements els = doc.select("a.dropdown-item");
            String pdfUrl = null;
            for (int i = 0; i < els.size(); i++) {
                String onClick = els.get(i).attr("onclick");
                Matcher matcher = onClickPattern.matcher(onClick);
                if (matcher.matches()) {
                    pdfUrl = String.format(
                        "https://www.pdfdrive.com/download.pdf?id=%s&h=%s&u=cache&ext=pdf",
                        matcher.group(1), matcher.group(2));
                    break;
                }
            }
            if (pdfUrl == null) {
                logger.atWarning().log("Cannot find PDF download link for '%s'", url);
            } else {
                crawler.download(
                    pdfUrl, Crawler.sanitizeFilename(titleEl.text()) + ".pdf", "", "",
                    TIMEOUT_MILLIS, MAX_TRIES);
            }
            return true;
        }
        return false;
    }

    // java -jar pdfdrive/target/pdfdrive-0.0.1-shaded.jar -d books/pdfdrive -c 9 63 66 67 71 72 -q "deep learning"
    public static void main(String[] args) throws ParseException, UnsupportedEncodingException {
        System.setProperty(
                "java.util.logging.SimpleFormatter.format",
                "%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS %4$s %5$s%6$s%n");
        AnsiColorConsoleHandler.replaceDefault();

        Options options = new Options();
        options.addOption(Option.builder("c").longOpt("category")
                                             .hasArgs()
                                             .argName("CATEGORY")
                                             .desc("categories to crawl")
                                             .build());
        options.addOption(Option.builder("d").longOpt("download-root")
                                             .hasArg()
                                             .required()
                                             .argName("DIR")
                                             .desc("number of threads to use")
                                             .build());
        options.addOption(Option.builder("l").longOpt("log-level")
                                             .hasArg()
                                             .argName("LEVEL")
                                             .desc("log level")
                                             .build());
        options.addOption(Option.builder("n").longOpt("num-threads")
                                             .hasArg()
                                             .argName("N")
                                             .desc("number of threads to use")
                                             .build());
        options.addOption(Option.builder("q").longOpt("query")
                                             .hasArgs()
                                             .argName("QUERY")
                                             .desc("search the queries")
                                             .build());
        options.addOption(Option.builder("t").longOpt("tag")
                                             .hasArgs()
                                             .argName("TAG")
                                             .desc("tags to crawl")
                                             .build());
        CommandLine cmd = new DefaultParser().parse(options, args);

        if (cmd.hasOption("log-level")) {
            System.setProperty(
                "java.util.logging.ConsoleHandler.level", cmd.getOptionValue("log-level"));
        }

        int numThreads = 3;
        if (cmd.hasOption("num-threads")) {
            numThreads = Integer.parseInt(cmd.getOptionValue("num-threads"));
        }
        String downloadRoot = cmd.getOptionValue("download-root");

        Crawler crawler = new Crawler(numThreads, downloadRoot);
        crawler.addHandler(new PdfdriveHandler());
        String[] categories = cmd.getOptionValues("category");
        if (categories != null) {
            for (String c : categories) {
                crawler.crawl(categoryUrl(c), TIMEOUT_MILLIS, MAX_TRIES);
            }
        }
        String[] tags = cmd.getOptionValues("tag");
        if (tags != null) {
            for (String t : tags) {
                crawler.crawl(tagUrl(t), TIMEOUT_MILLIS, MAX_TRIES);
            }
        }
        String[] queries = cmd.getOptionValues("query");
        if (queries != null) {
            for (String q : queries) {
                crawler.crawl(searchUrl(q), TIMEOUT_MILLIS, MAX_TRIES);
            }
        }
        crawler.shutdown();
    }

    public static String categoryUrl(String category) {
        return CATEGORY_URL_PREFIX + category;
    }

    public static String searchUrl(String query) throws UnsupportedEncodingException {
        return SEARCH_URL_PREFIX + URLEncoder.encode(query, "UTF-8");
    }

    public static String tagUrl(String tag) {
        return String.format(
            "https://www.pdfdrive.com/%s-books.html", tag.toLowerCase().replace(' ', '-'));
    }

    public final static String PROGRAMMING_URL = CATEGORY_URL_PREFIX + "63";

    private static final FluentLogger logger = FluentLogger.forEnclosingClass();
}
