package com.jarvis.commerce.product;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/products/{productId}/skus")
public class SkuController {

    private final SkuService skuService;

    public SkuController(SkuService skuService) {
        this.skuService = skuService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SkuResponse create(@PathVariable long productId, @Valid @RequestBody CreateSkuRequest request) {
        return skuService.create(productId, request);
    }

    @GetMapping
    public List<SkuResponse> list(@PathVariable long productId) {
        return skuService.list(productId);
    }

    @PutMapping("/{skuId}")
    public SkuResponse update(@PathVariable long productId, @PathVariable long skuId,
                              @Valid @RequestBody UpdateSkuRequest request) {
        return skuService.update(productId, skuId, request);
    }

    @DeleteMapping("/{skuId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long productId, @PathVariable long skuId) {
        skuService.delete(productId, skuId);
    }
}
