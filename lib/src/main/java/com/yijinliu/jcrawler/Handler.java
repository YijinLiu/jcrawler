package com.yijinliu.jcrawler;

import org.jsoup.nodes.Document;

public interface Handler {
    public String name();
    // Returns true if it's handled.
    public boolean Handle(String url, Document doc, Crawler crawler);
}
