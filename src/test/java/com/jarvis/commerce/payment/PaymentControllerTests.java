package com.jarvis.commerce.payment;

import com.jarvis.commerce.inventory.Inventory;
import com.jarvis.commerce.inventory.InventoryRepository;
import com.jarvis.commerce.messaging.outbox.OutboxEventRepository;
import com.jarvis.commerce.messaging.outbox.OutboxEventTypes;
import com.jarvis.commerce.order.CreateOrderItemRequest;
import com.jarvis.commerce.order.CreateOrderRequest;
import com.jarvis.commerce.order.CustomerOrderRepository;
import com.jarvis.commerce.order.OrderResponse;
import com.jarvis.commerce.order.OrderService;
import com.jarvis.commerce.order.OrderStatus;
import com.jarvis.commerce.product.Product;
import com.jarvis.commerce.product.ProductRepository;
import com.jarvis.commerce.product.Sku;
import com.jarvis.commerce.product.SkuRepository;
import com.jarvis.commerce.user.AddressRequest;
import com.jarvis.commerce.user.User;
import com.jarvis.commerce.user.UserAddress;
import com.jarvis.commerce.user.UserAddressRepository;
import com.jarvis.commerce.user.UserRepository;
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
class PaymentControllerTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ProductRepository productRepository;
    @Autowired private SkuRepository skuRepository;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private OrderService orderService;
    @Autowired private CustomerOrderRepository orderRepository;
    @Autowired private PaymentOrderRepository paymentRepository;
    @Autowired private PaymentTimeoutService paymentTimeoutService;
    @Autowired private UserRepository userRepository;
    @Autowired private UserAddressRepository addressRepository;
    @Autowired private OutboxEventRepository outboxRepository;

    private Long skuId;
    private Long orderId;

    @BeforeEach
    void createPendingOrder() {
        Product product = productRepository.save(new Product("Payment Course", null));
        Sku sku = skuRepository.save(new Sku(product, "PAY-SKU-" + System.nanoTime(), "Standard",
                new BigDecimal("99.90")));
        inventoryRepository.save(new Inventory(sku, 10));
        product.putOnSale();
        productRepository.flush();
        skuId = sku.getId();

        User user = userRepository.save(new User("payment-user-" + System.nanoTime(),
                "payment-" + System.nanoTime() + "@example.com", null));
        UserAddress address = addressRepository.save(new UserAddress(user.getId(), new AddressRequest(
                "家", "Jarvis", "13800138000", "北京", "北京市", "海淀区", "支付路1号", null, true), true));

        OrderResponse order = orderService.create(new CreateOrderRequest(
                user.getId(), address.getId(),
                List.of(new CreateOrderItemRequest(skuId, 2))));
        orderId = order.id();
    }

    @Test
    void createsPaymentIdempotently() throws Exception {
        createPayment("payment-key-1");

        mockMvc.perform(post("/api/payments")
                        .header("Idempotency-Key", "payment-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":" + orderId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));

        assertEquals(1, paymentRepository.count());
        String paymentNo = paymentRepository.findByOrderId(orderId).orElseThrow().getPaymentNo();
        assertEquals(1, outboxRepository.countByAggregateTypeAndAggregateIdAndEventType(
                "PAYMENT", paymentNo, OutboxEventTypes.PAYMENT_TIMEOUT_SCHEDULED));
    }

    @Test
    void successfulNotificationConfirmsOrderAndIsIdempotent() throws Exception {
        String paymentNo = createPayment("payment-key-2");
        String body = """
                {"notificationId":"notice-1","externalTransactionNo":"channel-tx-1","amount":199.80}
                """;

        sendSuccess(paymentNo, body).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
        sendSuccess(paymentNo, """
                {"notificationId":"notice-1-retry","externalTransactionNo":"channel-tx-1","amount":199.80}
                """).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
        sendSuccess(paymentNo, body).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        assertEquals(OrderStatus.PAID, orderRepository.findById(orderId).orElseThrow().getStatus());
        Inventory inventory = inventoryRepository.findBySkuId(skuId).orElseThrow();
        assertEquals(8, inventory.getAvailableQuantity());
        assertEquals(0, inventory.getReservedQuantity());
    }

    @Test
    void rejectsMismatchedAmount() throws Exception {
        String paymentNo = createPayment("payment-key-3");

        sendSuccess(paymentNo, """
                {"notificationId":"notice-2","externalTransactionNo":"channel-tx-2","amount":1.00}
                """)
                .andExpect(status().isConflict());

        assertEquals(PaymentStatus.PENDING,
                paymentRepository.findByPaymentNo(paymentNo).orElseThrow().getStatus());
        assertEquals(OrderStatus.PENDING_PAYMENT, orderRepository.findById(orderId).orElseThrow().getStatus());
    }

    @Test
    void failedPaymentCanBeRetried() throws Exception {
        String paymentNo = createPayment("payment-key-4");

        mockMvc.perform(post("/api/payments/{paymentNo}/mock-failure", paymentNo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notificationId\":\"notice-3\",\"reason\":\"declined\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));

        mockMvc.perform(post("/api/payments/{paymentNo}/retry", paymentNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void closingPaymentCancelsOrderAndReleasesStock() throws Exception {
        String paymentNo = createPayment("payment-key-5");

        mockMvc.perform(post("/api/payments/{paymentNo}/close", paymentNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        assertEquals(OrderStatus.CANCELLED, orderRepository.findById(orderId).orElseThrow().getStatus());
        Inventory inventory = inventoryRepository.findBySkuId(skuId).orElseThrow();
        assertEquals(10, inventory.getAvailableQuantity());
        assertEquals(0, inventory.getReservedQuantity());
    }

    @Test
    void expiresPaymentAndRejectsLateSuccess() throws Exception {
        String paymentNo = createPayment("payment-key-timeout");
        PaymentOrder payment = paymentRepository.findByPaymentNo(paymentNo).orElseThrow();

        int expired = paymentTimeoutService.expireDuePayments(payment.getExpiresAt().plusSeconds(1));

        assertEquals(1, expired);
        assertEquals(PaymentStatus.CLOSED, paymentRepository.findByPaymentNo(paymentNo).orElseThrow().getStatus());
        assertEquals(OrderStatus.CANCELLED, orderRepository.findById(orderId).orElseThrow().getStatus());
        Inventory inventory = inventoryRepository.findBySkuId(skuId).orElseThrow();
        assertEquals(10, inventory.getAvailableQuantity());
        assertEquals(0, inventory.getReservedQuantity());

        sendSuccess(paymentNo, """
                {"notificationId":"late-notice","externalTransactionNo":"late-tx","amount":199.80}
                """).andExpect(status().isConflict());
    }

    private String createPayment(String idempotencyKey) throws Exception {
        mockMvc.perform(post("/api/payments")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":" + orderId + "}"))
                .andExpect(status().isCreated());
        return paymentRepository.findByOrderId(orderId).orElseThrow().getPaymentNo();
    }

    private org.springframework.test.web.servlet.ResultActions sendSuccess(String paymentNo, String body)
            throws Exception {
        return mockMvc.perform(post("/api/payments/{paymentNo}/mock-success", paymentNo)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
