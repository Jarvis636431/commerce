package com.jarvis.commerce.inventory;

import com.jarvis.commerce.common.ConflictException;
import com.jarvis.commerce.product.Sku;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "inventory")
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sku_id", nullable = false, unique = true)
    private Sku sku;

    @Column(name = "available_quantity", nullable = false)
    private int availableQuantity;

    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Inventory() {
    }

    public Inventory(Sku sku, int initialQuantity) {
        this.sku = sku;
        this.availableQuantity = initialQuantity;
        this.reservedQuantity = 0;
    }

    public void increase(int quantity) {
        availableQuantity = Math.addExact(availableQuantity, quantity);
    }

    public void reserve(int quantity) {
        if (availableQuantity < quantity) {
            throw new ConflictException("Insufficient stock: requested %d, available %d"
                    .formatted(quantity, availableQuantity));
        }
        availableQuantity -= quantity;
        reservedQuantity = Math.addExact(reservedQuantity, quantity);
    }

    public void confirm(int quantity) {
        ensureEnoughReserved(quantity);
        reservedQuantity -= quantity;
    }

    public void release(int quantity) {
        ensureEnoughReserved(quantity);
        reservedQuantity -= quantity;
        availableQuantity = Math.addExact(availableQuantity, quantity);
    }

    private void ensureEnoughReserved(int quantity) {
        if (reservedQuantity < quantity) {
            throw new ConflictException("Insufficient reserved stock: requested %d, reserved %d"
                    .formatted(quantity, reservedQuantity));
        }
    }

    @PrePersist
    @PreUpdate
    void updateTimestamp() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() { return id; }
    public Sku getSku() { return sku; }
    public int getAvailableQuantity() { return availableQuantity; }
    public int getReservedQuantity() { return reservedQuantity; }
    public long getVersion() { return version; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
