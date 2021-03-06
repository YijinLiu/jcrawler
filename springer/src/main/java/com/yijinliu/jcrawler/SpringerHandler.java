package com.yijinliu.jcrawler;

import com.google.common.flogger.FluentLogger;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class SpringerHandler implements Handler {

    public final static String ML_65_URL =
        "https://towardsdatascience.com/springer-has-released-65-machine-learning-and-data-books-for-free-961f8181f189";

    public final static String BOOK_URL_PREFIX = "http://link.springer.com/openurl?";

    public final static int TIMEOUT_MILLIS = 5000;

    public final static int MAX_TRIES = 2;

    public String name() {
        return "springer";
    }

    @Override
    public boolean Handle(String url, Document doc, Crawler crawler) {
        if (url == ML_65_URL) {
            doc.getElementsByTag("a").forEach((el) -> {
                String bookUrl = el.attr("href");
                if (bookUrl.startsWith(BOOK_URL_PREFIX)) {
                    crawler.crawl(bookUrl, TIMEOUT_MILLIS, MAX_TRIES);
                }
            });
            return true;
        } else if (url.startsWith(BOOK_URL_PREFIX)) {
            Element titleEl = doc.selectFirst(".page-title > h1");
            Elements pdfEls = doc.getElementsByAttributeValue(
                "title", "Download this book in PDF format");
            if (titleEl == null || pdfEls.isEmpty()) {
                logger.atWarning().log("Failed to find title/pdf element for '%s'.", url);
                if (!crawler.retryCrawl(url, TIMEOUT_MILLIS, MAX_TRIES)) {
                    logger.atWarning().log("Too many failures on '%s', won't retry.", url);
                }
            } else {
                crawler.download(
                    pdfEls.first().absUrl("href"),
                    Crawler.sanitizeFilename(
                        titleEl.text()) + ".pdf", "", "", TIMEOUT_MILLIS, MAX_TRIES);
            }
            return true;
        }
        return false;
    }

    // java -jar springer/target/springer-0.0.1-shaded.jar -d books/springer
    public static void main(String[] args) throws ParseException {
        System.setProperty(
                "java.util.logging.SimpleFormatter.format",
                "%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS %4$s %5$s%6$s%n");
        AnsiColorConsoleHandler.replaceDefault();

        Options options = new Options();
        options.addOption(Option.builder("d").longOpt("download-root")
                                             .hasArg()
                                             .required()
                                             .argName("DIR")
                                             .desc("number of threads to use")
                                             .build());
        options.addOption(Option.builder("lf").longOpt("log-file")
                                              .hasArg()
                                              .argName("LOGFILE")
                                              .desc("downloaded log file")
                                              .build());
        options.addOption(Option.builder("ll").longOpt("log-level")
                                             .hasArg()
                                             .argName("LEVEL")
                                             .desc("log level")
                                             .build());
        options.addOption(Option.builder("n").longOpt("num-threads")
                                             .hasArg()
                                             .argName("N")
                                             .desc("number of threads to use")
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

        Crawler crawler = new Crawler(numThreads, downloadRoot, cmd.getOptionValue("log-file"));
        crawler.addHandler(new SpringerHandler());
        crawler.crawl(ML_65_URL, TIMEOUT_MILLIS, MAX_TRIES);
        crawler.shutdown();
    }

    private static final FluentLogger logger = FluentLogger.forEnclosingClass();
}
