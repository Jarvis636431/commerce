package com.jarvis.commerce.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    Optional<Inventory> findBySkuId(Long skuId);
    boolean existsBySkuId(Long skuId);
}
