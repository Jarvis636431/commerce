package com.jarvis.commerce.product;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.time.OffsetDateTime;
import org.springframework.data.domain.Pageable;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {
    Optional<ProductImage> findByIdAndProductId(long id, long productId);
    List<ProductImage> findByProductIdAndStatusOrderByPrimaryImageDescSortOrderAscIdAsc(long productId, ProductImageStatus status);
    Optional<ProductImage> findFirstByProductIdAndStatusAndPrimaryImageTrue(long productId, ProductImageStatus status);
    List<ProductImage> findAllByProductId(long productId);
    List<ProductImage> findByStatusAndCreatedAtBeforeOrderById(ProductImageStatus status, OffsetDateTime cutoff,
                                                               Pageable pageable);
}
