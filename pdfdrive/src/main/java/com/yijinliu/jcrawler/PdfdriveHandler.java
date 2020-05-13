package com.yijinliu.jcrawler;

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

    public final static String MATH_URL = CATEGORY_URL_PREFIX + "67";

    public final static String PROGRAMMING_URL = CATEGORY_URL_PREFIX + "63";

    public final static Pattern bookUrlPattern = Pattern.compile(".+-e[0-9]+[.]html");

    public final static Pattern onClickPattern = Pattern.compile(
        "initConverter[(]'([0-9]+)','([0-9a-f]+)','EPUB'[)];.*");

    public final static int TIMEOUT_MILLIS = 5000;

    public final static int MAX_TRIES = 2;

    public String name() {
        return "springer";
    }

    public boolean Handle(String url, Document doc, Crawler crawler) {
        if (url.startsWith(CATEGORY_URL_PREFIX)) {
            doc.select(".files-new a").forEach((el) -> {
                String bookUrl = el.absUrl("href");
                if (bookUrlPattern.matcher(bookUrl).matches()) {
                    crawler.crawl(bookUrl, TIMEOUT_MILLIS, MAX_TRIES);
                } else {
                    logger.atWarning().log("Unknown URL '%s'.", bookUrl);
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
                crawler.download(pdfUrl, titleEl.text() + ".pdf", MAX_TRIES);
            }
            return true;
        }
        return false;
    }

    // java -jar springer/target/pdfdrive-0.0.1-shaded.jar -d books/pdfdrive
    public static void main(String[] args) throws ParseException {
        Options options = new Options();
        options.addOption(Option.builder("n").longOpt("num-threads")
                                             .hasArg()
                                             .argName("N")
                                             .desc("number of threads to use")
                                             .build());
        options.addOption(Option.builder("d").longOpt("download-root")
                                             .hasArg()
                                             .required()
                                             .argName("DIR")
                                             .desc("number of threads to use")
                                             .build());
        CommandLine cmd = new DefaultParser().parse(options, args);

        int numThreads = 3;
        if (cmd.hasOption("num-threads")) {
            numThreads = Integer.parseInt(cmd.getOptionValue("num-threads"));
        }
        String downloadRoot = cmd.getOptionValue("download-root");

        Crawler crawler = new Crawler(numThreads, downloadRoot);
        crawler.addHandler(new PdfdriveHandler());
        crawler.crawl(MATH_URL, TIMEOUT_MILLIS, MAX_TRIES);
        crawler.crawl(PROGRAMMING_URL, TIMEOUT_MILLIS, MAX_TRIES);
        crawler.shutdown();
    }

    private static final FluentLogger logger = FluentLogger.forEnclosingClass();
}
