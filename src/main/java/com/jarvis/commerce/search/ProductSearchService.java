package com.jarvis.commerce.search;

import com.jarvis.commerce.common.PageResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "commerce.search.enabled", havingValue = "true", matchIfMissing = true)
public class ProductSearchService {
    private final ProductSearchRepository repository;

    public ProductSearchService(ProductSearchRepository repository) { this.repository = repository; }

    public PageResponse<ProductSearchResponse> search(String keyword, Pageable pageable) {
        return PageResponse.from(repository.searchOnSale(keyword.trim(), pageable), ProductSearchResponse::from);
    }
}
