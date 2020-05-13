package com.yijinliu.jcrawler;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class PdfdriveHandlerTest extends TestCase {
    public PdfdriveHandlerTest(String testName) {
        super(testName);
    }

    public static Test suite() {
        return new TestSuite(PdfdriveHandlerTest.class);
    }

    public void testSpringerHandler() {
        PdfdriveHandler handler = new PdfdriveHandler();
    }
}
