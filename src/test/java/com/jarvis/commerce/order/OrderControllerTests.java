package com.jarvis.commerce.order;

import com.jarvis.commerce.inventory.Inventory;
import com.jarvis.commerce.inventory.InventoryRepository;
import com.jarvis.commerce.product.Product;
import com.jarvis.commerce.product.ProductRepository;
import com.jarvis.commerce.product.Sku;
import com.jarvis.commerce.product.SkuRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OrderControllerTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ProductRepository productRepository;
    @Autowired private SkuRepository skuRepository;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private CustomerOrderRepository orderRepository;
    @Autowired private InventoryReservationRepository reservationRepository;
    @Autowired private OrderService orderService;

    private Long skuId;

    @BeforeEach
    void createOnSaleSkuWithInventory() {
        Product product = productRepository.save(new Product("Java Course", null));
        Sku sku = skuRepository.save(new Sku(product, "ORDER-SKU-" + System.nanoTime(), "Standard",
                new BigDecimal("99.90")));
        inventoryRepository.save(new Inventory(sku, 10));
        product.putOnSale();
        productRepository.flush();
        skuId = sku.getId();
    }

    @Test
    void createsOrderAndReservesInventory() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createOrderJson(2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.totalAmount").value(199.80))
                .andExpect(jsonPath("$.items[0].skuId").value(skuId))
                .andExpect(jsonPath("$.items[0].unitPrice").value(99.90))
                .andExpect(jsonPath("$.items[0].quantity").value(2));

        Inventory inventory = inventoryRepository.findBySkuId(skuId).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(8, inventory.getAvailableQuantity());
        org.junit.jupiter.api.Assertions.assertEquals(2, inventory.getReservedQuantity());
    }

    @Test
    void paysOrderAndConfirmsReservation() throws Exception {
        long orderId = createOrder(3);

        org.junit.jupiter.api.Assertions.assertEquals(OrderStatus.PAID, orderService.confirmPayment(orderId).status());

        Inventory inventory = inventoryRepository.findBySkuId(skuId).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(7, inventory.getAvailableQuantity());
        org.junit.jupiter.api.Assertions.assertEquals(0, inventory.getReservedQuantity());
        org.junit.jupiter.api.Assertions.assertEquals(
                ReservationStatus.CONFIRMED,
                reservationRepository.findAllByOrderIdOrderByIdAsc(orderId).getFirst().getStatus());
    }

    @Test
    void cancelsOrderAndReleasesReservation() throws Exception {
        long orderId = createOrder(4);

        mockMvc.perform(post("/api/orders/{id}/cancel", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        Inventory inventory = inventoryRepository.findBySkuId(skuId).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(10, inventory.getAvailableQuantity());
        org.junit.jupiter.api.Assertions.assertEquals(0, inventory.getReservedQuantity());
        org.junit.jupiter.api.Assertions.assertEquals(
                ReservationStatus.RELEASED,
                reservationRepository.findAllByOrderIdOrderByIdAsc(orderId).getFirst().getStatus());
    }

    @Test
    void completesOnlyPaidOrder() throws Exception {
        long orderId = createOrder(1);

        mockMvc.perform(post("/api/orders/{id}/complete", orderId))
                .andExpect(status().isConflict());

        orderService.confirmPayment(orderId);
        mockMvc.perform(post("/api/orders/{id}/complete", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void getsCreatedOrder() throws Exception {
        long orderId = createOrder(1);

        mockMvc.perform(get("/api/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId))
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    private long createOrder(int quantity) throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createOrderJson(quantity)))
                .andExpect(status().isCreated());
        return orderRepository.findAll().getLast().getId();
    }

    private String createOrderJson(int quantity) {
        return "{\"items\":[{\"skuId\":" + skuId + ",\"quantity\":" + quantity + "}]}";
    }
}
