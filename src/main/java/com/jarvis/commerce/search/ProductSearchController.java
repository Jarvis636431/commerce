package com.jarvis.commerce.search;

import com.jarvis.commerce.common.PageResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/search/products")
@ConditionalOnProperty(name = "commerce.search.enabled", havingValue = "true", matchIfMissing = true)
public class ProductSearchController {
    private final ProductSearchService service;

    public ProductSearchController(ProductSearchService service) { this.service = service; }

    @GetMapping
    public PageResponse<ProductSearchResponse> search(
            @RequestParam("q") @NotBlank @Size(max = 100) String keyword,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.search(keyword, pageable);
    }
}
