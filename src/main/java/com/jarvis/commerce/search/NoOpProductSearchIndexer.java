package com.jarvis.commerce.search;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "commerce.search.enabled", havingValue = "false")
public class NoOpProductSearchIndexer implements ProductSearchIndexer {
    @Override public void index(long productId) {}
    @Override public void delete(long productId) {}
    @Override public long rebuild() { return 0; }
}
