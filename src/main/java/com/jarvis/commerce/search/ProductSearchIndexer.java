package com.jarvis.commerce.search;

public interface ProductSearchIndexer {
    void index(long productId);
    void delete(long productId);
    long rebuild();
}
