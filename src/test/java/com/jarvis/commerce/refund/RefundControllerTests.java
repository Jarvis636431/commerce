package com.jarvis.commerce.refund;

import com.jarvis.commerce.inventory.Inventory;
import com.jarvis.commerce.inventory.InventoryRepository;
import com.jarvis.commerce.order.*;
import com.jarvis.commerce.payment.*;
import com.jarvis.commerce.product.*;
import com.jarvis.commerce.user.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RefundControllerTests {
    @Autowired MockMvc mockMvc;
    @Autowired ProductRepository productRepository;
    @Autowired SkuRepository skuRepository;
    @Autowired InventoryRepository inventoryRepository;
    @Autowired UserRepository userRepository;
    @Autowired UserAddressRepository addressRepository;
    @Autowired OrderService orderService;
    @Autowired CustomerOrderRepository orderRepository;
    @Autowired PaymentService paymentService;
    @Autowired RefundOrderRepository refundRepository;

    private Long orderId;

    @BeforeEach
    void createPaidOrder() {
        Product product = productRepository.save(new Product("Refund Course", null));
        Sku sku = skuRepository.save(new Sku(product, "REF-SKU-" + System.nanoTime(), "Standard",
                new BigDecimal("88.00")));
        inventoryRepository.save(new Inventory(sku, 10));
        product.putOnSale();
        User user = userRepository.save(new User("refund-user-" + System.nanoTime(),
                "refund-" + System.nanoTime() + "@example.com", null));
        UserAddress address = addressRepository.save(new UserAddress(user.getId(), new AddressRequest(
                "家", "Buyer", "13800138000", "北京", "北京市", "海淀区", "退款路1号", null, true), true));
        orderId = orderService.create(new CreateOrderRequest(user.getId(), address.getId(),
                List.of(new CreateOrderItemRequest(sku.getId(), 1)))).id();
        PaymentResponse payment = paymentService.create(new CreatePaymentRequest(orderId), "pay-" + System.nanoTime());
        paymentService.handleSuccess(payment.paymentNo(), new PaymentSuccessNotification(
                "pay-notice-" + System.nanoTime(), "channel-pay-" + System.nanoTime(), new BigDecimal("88.00")));
    }

    @Test
    void createsFullRefundIdempotentlyAndCompletesFromCallback() throws Exception {
        createRefund("refund-key-1").andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(88.00))
                .andExpect(jsonPath("$.status").value("PENDING"));
        createRefund("refund-key-1").andExpect(status().isCreated());

        RefundOrder refund = refundRepository.findAll().getFirst();
        assertEquals(1, refundRepository.count());
        assertEquals(OrderStatus.REFUNDING, orderRepository.findById(orderId).orElseThrow().getStatus());

        String body = """
                {"notificationId":"refund-notice-1","externalRefundNo":"channel-refund-1"}
                """;
        mockMvc.perform(post("/api/refunds/{refundNo}/mock-success", refund.getRefundNo())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCESS"));
        mockMvc.perform(post("/api/refunds/{refundNo}/mock-success", refund.getRefundNo())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCESS"));

        assertEquals(OrderStatus.REFUNDED, orderRepository.findById(orderId).orElseThrow().getStatus());
    }

    @Test
    void failedRefundRestoresPreviousOrderStatus() throws Exception {
        createRefund("refund-key-2").andExpect(status().isCreated());
        String refundNo = refundRepository.findAll().getFirst().getRefundNo();

        mockMvc.perform(post("/api/refunds/{refundNo}/mock-failure", refundNo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notificationId\":\"refund-notice-2\",\"reason\":\"channel unavailable\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FAILED"));

        assertEquals(OrderStatus.PAID, orderRepository.findById(orderId).orElseThrow().getStatus());
    }

    private org.springframework.test.web.servlet.ResultActions createRefund(String key) throws Exception {
        return mockMvc.perform(post("/api/refunds")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":" + orderId + ",\"reason\":\"不再需要\"}"));
    }
}
