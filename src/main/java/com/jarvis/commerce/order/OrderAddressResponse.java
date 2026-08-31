package com.jarvis.commerce.order;

public record OrderAddressResponse(String receiverName, String phone, String province, String city,
                                   String district, String detailAddress, String postalCode) {
    static OrderAddressResponse from(CustomerOrder order) {
        return new OrderAddressResponse(order.getReceiverName(), order.getReceiverPhone(), order.getProvince(),
                order.getCity(), order.getDistrict(), order.getDetailAddress(), order.getPostalCode());
    }
}
