package com.jarvis.commerce.product;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {
    Optional<ProductImage> findByIdAndProductId(long id, long productId);
    List<ProductImage> findByProductIdAndStatusOrderById(long productId, ProductImageStatus status);
}
