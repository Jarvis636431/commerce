package com.jarvis.commerce.order;

import com.jarvis.commerce.common.ConflictException;
import com.jarvis.commerce.product.Sku;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "inventory_reservation")
public class InventoryReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private CustomerOrder order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sku_id", nullable = false)
    private Sku sku;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected InventoryReservation() {
    }

    public InventoryReservation(CustomerOrder order, Sku sku, int quantity) {
        this.order = order;
        this.sku = sku;
        this.quantity = quantity;
        this.status = ReservationStatus.RESERVED;
    }

    public void confirm() {
        requireReserved();
        status = ReservationStatus.CONFIRMED;
    }

    public void release() {
        requireReserved();
        status = ReservationStatus.RELEASED;
    }

    private void requireReserved() {
        if (status != ReservationStatus.RESERVED) {
            throw new ConflictException("Inventory reservation has already been finalized");
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

    public Long getSkuId() { return sku.getId(); }
    public int getQuantity() { return quantity; }
    public ReservationStatus getStatus() { return status; }
}
