package com.jarvis.commerce.inventory;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/skus/{skuId}/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PutMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryResponse initialize(@PathVariable long skuId,
                                        @Valid @RequestBody InitializeInventoryRequest request) {
        return inventoryService.initialize(skuId, request);
    }

    @GetMapping
    public InventoryResponse get(@PathVariable long skuId) {
        return inventoryService.get(skuId);
    }

    @PostMapping("/increase")
    public InventoryResponse increase(@PathVariable long skuId,
                                      @Valid @RequestBody InventoryQuantityRequest request) {
        return inventoryService.increase(skuId, request);
    }

    @PostMapping("/reserve")
    public InventoryResponse reserve(@PathVariable long skuId,
                                     @Valid @RequestBody InventoryQuantityRequest request) {
        return inventoryService.reserve(skuId, request);
    }

    @PostMapping("/confirm")
    public InventoryResponse confirm(@PathVariable long skuId,
                                     @Valid @RequestBody InventoryQuantityRequest request) {
        return inventoryService.confirm(skuId, request);
    }

    @PostMapping("/release")
    public InventoryResponse release(@PathVariable long skuId,
                                     @Valid @RequestBody InventoryQuantityRequest request) {
        return inventoryService.release(skuId, request);
    }
}
