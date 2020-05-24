package com.yijinliu.jcrawler;

import java.util.ArrayList;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class DownloadedFileTest extends TestCase {
    public DownloadedFileTest(String testName) {
        super(testName);
    }

    public static Test suite() {
        return new TestSuite(DownloadedFileTest.class);
    }

    public void testDownloadedFile() {
        DownloadedFile df =
            DownloadedFile.builder().setUrl("test-url").setFile("test-file").build();
        assertEquals("test-url", df.url());
        assertEquals("test-file", df.file());
    }

    public void testToJson() {
        DownloadedFile df =
            DownloadedFile.builder().setUrl("test-url").setFile("test-file").build();
        assertEquals("{\"url\":\"test-url\",\"file\":\"test-file\"}", df.toJson());

        ArrayList<DownloadedFile> dfs = new ArrayList<>();
        dfs.add(df);
        assertEquals("[{\"url\":\"test-url\",\"file\":\"test-file\"}]", DownloadedFile.toJson(dfs));
    }
}
