package com.jarvis.commerce.product;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SkuRepository extends JpaRepository<Sku, Long> {
    List<Sku> findAllByProductIdOrderByIdAsc(Long productId);
    boolean existsByCode(String code);
    boolean existsByProductId(Long productId);
    void deleteAllByProductId(Long productId);
}
