package com.jarvis.commerce.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "product")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Product() {
    }

    public Product(String name, String description) {
        this.name = name;
        this.description = description;
        this.status = ProductStatus.DRAFT;
    }

    public void update(String name, String description) {
        ensureNotOnSale("On-sale products cannot be edited");
        this.name = name;
        this.description = description;
    }

    public void putOnSale() {
        if (status == ProductStatus.ON_SALE) {
            throw new ProductStateException("Product is already on sale");
        }
        status = ProductStatus.ON_SALE;
    }

    public void takeOffSale() {
        if (status != ProductStatus.ON_SALE) {
            throw new ProductStateException("Only on-sale products can be taken off sale");
        }
        status = ProductStatus.OFF_SALE;
    }

    public void ensureDeletable() {
        ensureNotOnSale("On-sale products cannot be deleted");
    }

    public void ensureSkuEditable() {
        ensureNotOnSale("SKUs of an on-sale product cannot be changed");
    }

    private void ensureNotOnSale(String message) {
        if (status == ProductStatus.ON_SALE) {
            throw new ProductStateException(message);
        }
    }

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public ProductStatus getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
