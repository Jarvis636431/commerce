package com.jarvis.commerce.refund;

import com.jarvis.commerce.inventory.Inventory;
import com.jarvis.commerce.inventory.InventoryRepository;
import com.jarvis.commerce.order.*;
import com.jarvis.commerce.payment.*;
import com.jarvis.commerce.messaging.outbox.OutboxEventRepository;
import com.jarvis.commerce.messaging.outbox.OutboxEventTypes;
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
    @Autowired OutboxEventRepository outboxRepository;

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
        assertEquals(1, outboxRepository.countByAggregateTypeAndAggregateIdAndEventType(
                "REFUND", refund.getRefundNo(), OutboxEventTypes.REFUND_REQUESTED));
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

    @Test
    void supportsSequentialPartialRefundsUntilFullyRefunded() throws Exception {
        createRefund("partial-key-1", "30.00").andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(30.00));
        RefundOrder first = refundRepository.findAll().getFirst();
        succeed(first.getRefundNo(), "partial-notice-1", "channel-partial-1");
        assertEquals(OrderStatus.PAID, orderRepository.findById(orderId).orElseThrow().getStatus());

        createRefund("partial-key-2", "58.00").andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(58.00));
        RefundOrder second = refundRepository.findAllByPaymentIdOrderByIdAsc(first.getPayment().getId()).get(1);
        succeed(second.getRefundNo(), "partial-notice-2", "channel-partial-2");

        assertEquals(2, refundRepository.count());
        assertEquals(OrderStatus.REFUNDED, orderRepository.findById(orderId).orElseThrow().getStatus());
    }

    @Test
    void rejectsAmountAboveRemainingRefundableBalance() throws Exception {
        createRefund("partial-limit-1", "30.00").andExpect(status().isCreated());
        RefundOrder first = refundRepository.findAll().getFirst();
        succeed(first.getRefundNo(), "limit-notice-1", "channel-limit-1");

        createRefund("partial-limit-2", "58.01")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Refund amount exceeds remaining refundable amount: 58.00"));

        assertEquals(1, refundRepository.count());
    }

    @Test
    void rejectsSecondRefundWhileFirstIsStillActive() throws Exception {
        createRefund("active-key-1", "30.00").andExpect(status().isCreated());
        createRefund("active-key-2", "20.00").andExpect(status().isConflict());
        assertEquals(1, refundRepository.count());
    }

    private org.springframework.test.web.servlet.ResultActions createRefund(String key) throws Exception {
        return mockMvc.perform(post("/api/refunds")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":" + orderId + ",\"reason\":\"不再需要\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions createRefund(String key, String amount)
            throws Exception {
        return mockMvc.perform(post("/api/refunds")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":" + orderId + ",\"reason\":\"部分退款\",\"amount\":" + amount + "}"));
    }

    private void succeed(String refundNo, String notificationId, String externalRefundNo) throws Exception {
        mockMvc.perform(post("/api/refunds/{refundNo}/mock-success", refundNo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notificationId\":\"" + notificationId +
                                "\",\"externalRefundNo\":\"" + externalRefundNo + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCESS"));
    }
}
