package com.jarvis.commerce.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    Optional<Inventory> findBySkuId(Long skuId);
    boolean existsBySkuId(Long skuId);
    List<Inventory> findAllBySkuIdIn(Collection<Long> skuIds);
}
