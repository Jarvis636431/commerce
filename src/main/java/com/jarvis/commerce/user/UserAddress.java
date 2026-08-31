package com.jarvis.commerce.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "user_address")
public class UserAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 50)
    private String label;

    @Column(name = "receiver_name", nullable = false, length = 100)
    private String receiverName;

    @Column(nullable = false, length = 32)
    private String phone;

    @Column(nullable = false, length = 100)
    private String province;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(nullable = false, length = 100)
    private String district;

    @Column(name = "detail_address", nullable = false, length = 500)
    private String detailAddress;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(name = "is_default", nullable = false)
    private boolean defaultAddress;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected UserAddress() {
    }

    public UserAddress(Long userId, AddressRequest request, boolean defaultAddress) {
        this.userId = userId;
        updateFields(request);
        this.defaultAddress = defaultAddress;
    }

    public void update(AddressRequest request) {
        updateFields(request);
    }

    private void updateFields(AddressRequest request) {
        label = request.label().trim();
        receiverName = request.receiverName().trim();
        phone = request.phone().trim();
        province = request.province().trim();
        city = request.city().trim();
        district = request.district().trim();
        detailAddress = request.detailAddress().trim();
        postalCode = request.postalCode() == null || request.postalCode().isBlank()
                ? null : request.postalCode().trim();
    }

    public void markDefault() { defaultAddress = true; }
    public void clearDefault() { defaultAddress = false; }

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = OffsetDateTime.now(ZoneOffset.UTC); }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getLabel() { return label; }
    public String getReceiverName() { return receiverName; }
    public String getPhone() { return phone; }
    public String getProvince() { return province; }
    public String getCity() { return city; }
    public String getDistrict() { return district; }
    public String getDetailAddress() { return detailAddress; }
    public String getPostalCode() { return postalCode; }
    public boolean isDefaultAddress() { return defaultAddress; }
    public long getVersion() { return version; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
