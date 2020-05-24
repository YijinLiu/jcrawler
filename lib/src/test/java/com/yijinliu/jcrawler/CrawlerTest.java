package com.yijinliu.jcrawler;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class CrawlerTest extends TestCase {
    public CrawlerTest(String testName) {
        super(testName);
    }

    public static Test suite() {
        return new TestSuite(CrawlerTest.class);
    }

    public void testCrawler() {
        Crawler crawler = new Crawler(3, System.getProperty("java.io.tmpdir"), "");
        crawler.shutdown();
    }
}
