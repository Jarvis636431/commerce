package com.jarvis.commerce.search;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/search/products")
@ConditionalOnProperty(name = "commerce.search.enabled", havingValue = "true", matchIfMissing = true)
public class ProductSearchAdminController {
    private final ProductSearchIndexer indexer;

    public ProductSearchAdminController(ProductSearchIndexer indexer) { this.indexer = indexer; }

    @PostMapping("/rebuild")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Long> rebuild() { return Map.of("indexed", indexer.rebuild()); }
}
