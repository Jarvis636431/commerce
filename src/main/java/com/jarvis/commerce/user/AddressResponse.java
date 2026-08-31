package com.jarvis.commerce.user;

import java.time.OffsetDateTime;

public record AddressResponse(Long id, Long userId, String label, String receiverName, String phone,
                              String province, String city, String district, String detailAddress,
                              String postalCode, boolean defaultAddress, long version,
                              OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    public static AddressResponse from(UserAddress address) {
        return new AddressResponse(address.getId(), address.getUserId(), address.getLabel(),
                address.getReceiverName(), address.getPhone(), address.getProvince(), address.getCity(),
                address.getDistrict(), address.getDetailAddress(), address.getPostalCode(),
                address.isDefaultAddress(), address.getVersion(), address.getCreatedAt(), address.getUpdatedAt());
    }
}
