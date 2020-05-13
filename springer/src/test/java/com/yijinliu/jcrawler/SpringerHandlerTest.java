package com.yijinliu.jcrawler;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class SpringerHandlerTest extends TestCase {
    public SpringerHandlerTest(String testName) {
        super(testName);
    }

    public static Test suite() {
        return new TestSuite(SpringerHandlerTest.class);
    }

    public void testSpringerHandler() {
        SpringerHandler handler = new SpringerHandler();
    }
}
