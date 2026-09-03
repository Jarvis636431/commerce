package com.jarvis.commerce.product;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "product_image")
public class ProductImage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "product_id", nullable = false)
    private Long productId;
    @Column(name = "object_key", nullable = false, unique = true, length = 500)
    private String objectKey;
    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;
    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;
    @Column(name = "declared_size", nullable = false)
    private long declaredSize;
    @Column(name = "actual_size")
    private Long actualSize;
    @Column(length = 200)
    private String etag;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private ProductImageStatus status;
    @Version @Column(nullable = false)
    private long version;
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ProductImage() { }

    ProductImage(long productId, String objectKey, String originalFilename, String contentType, long declaredSize) {
        this.productId = productId;
        this.objectKey = objectKey;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.declaredSize = declaredSize;
        this.status = ProductImageStatus.PENDING;
    }

    void markReady(long actualSize, String etag) {
        this.actualSize = actualSize;
        this.etag = etag;
        this.status = ProductImageStatus.READY;
    }

    @PrePersist void onCreate() {
        createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        updatedAt = createdAt;
    }

    @PreUpdate void onUpdate() { updatedAt = OffsetDateTime.now(ZoneOffset.UTC); }

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public String getObjectKey() { return objectKey; }
    public String getOriginalFilename() { return originalFilename; }
    public String getContentType() { return contentType; }
    public long getDeclaredSize() { return declaredSize; }
    public Long getActualSize() { return actualSize; }
    public String getEtag() { return etag; }
    public ProductImageStatus getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
