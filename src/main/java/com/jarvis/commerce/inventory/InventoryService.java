package com.jarvis.commerce.inventory;

import com.jarvis.commerce.common.ConflictException;
import com.jarvis.commerce.common.ResourceNotFoundException;
import com.jarvis.commerce.product.Sku;
import com.jarvis.commerce.product.SkuRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Consumer;

@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final SkuRepository skuRepository;

    public InventoryService(InventoryRepository inventoryRepository, SkuRepository skuRepository) {
        this.inventoryRepository = inventoryRepository;
        this.skuRepository = skuRepository;
    }

    @Transactional
    public InventoryResponse initialize(long skuId, InitializeInventoryRequest request) {
        if (inventoryRepository.existsBySkuId(skuId)) {
            throw new ConflictException("Inventory for SKU %d already exists".formatted(skuId));
        }
        Sku sku = skuRepository.findById(skuId)
                .orElseThrow(() -> new ResourceNotFoundException("SKU %d was not found".formatted(skuId)));
        return InventoryResponse.from(inventoryRepository.save(new Inventory(sku, request.quantity())));
    }

    @Transactional(readOnly = true)
    public InventoryResponse get(long skuId) {
        return InventoryResponse.from(findInventory(skuId));
    }

    @Transactional
    public InventoryResponse increase(long skuId, InventoryQuantityRequest request) {
        return change(skuId, inventory -> inventory.increase(request.quantity()));
    }

    @Transactional
    public InventoryResponse reserve(long skuId, InventoryQuantityRequest request) {
        return change(skuId, inventory -> inventory.reserve(request.quantity()));
    }

    @Transactional
    public InventoryResponse confirm(long skuId, InventoryQuantityRequest request) {
        return change(skuId, inventory -> inventory.confirm(request.quantity()));
    }

    @Transactional
    public InventoryResponse release(long skuId, InventoryQuantityRequest request) {
        return change(skuId, inventory -> inventory.release(request.quantity()));
    }

    private InventoryResponse change(long skuId, Consumer<Inventory> operation) {
        Inventory inventory = findInventory(skuId);
        operation.accept(inventory);
        inventoryRepository.flush();
        return InventoryResponse.from(inventory);
    }

    private Inventory findInventory(long skuId) {
        return inventoryRepository.findBySkuId(skuId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Inventory for SKU %d was not found".formatted(skuId)));
    }
}
