package com.jarvis.commerce.cart;

import com.jarvis.commerce.inventory.Inventory;
import com.jarvis.commerce.inventory.InventoryRepository;
import com.jarvis.commerce.order.CustomerOrderRepository;
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

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CartCheckoutTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private UserAddressRepository addressRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private SkuRepository skuRepository;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private CustomerOrderRepository orderRepository;
    @Autowired private CartStore cartStore;

    private Long userId;
    private Long addressId;
    private Long skuId;

    @BeforeEach
    void setUp() {
        long unique = System.nanoTime();
        User user = userRepository.save(new User("checkout-" + unique, "checkout-" + unique + "@example.com", null));
        UserAddress address = addressRepository.save(new UserAddress(user.getId(), new AddressRequest(
                "家", "Jarvis", "13800138000", "北京", "北京市", "海淀区", "结算路1号", null, true), true));
        Product product = productRepository.save(new Product("Checkout Course", null));
        Sku sku = skuRepository.save(new Sku(product, "CHECKOUT-" + unique, "Standard", new BigDecimal("30.00")));
        inventoryRepository.save(new Inventory(sku, 5));
        product.putOnSale();
        productRepository.saveAndFlush(product);
        userId = user.getId();
        addressId = address.getId();
        skuId = sku.getId();
        cartStore.clear(userId);
    }

    @Test
    void createsOrderThenRemovesCommittedCartItems() throws Exception {
        cartStore.put(userId, skuId, 2);

        mockMvc.perform(post("/api/users/{userId}/cart/checkout", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressId\":" + addressId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.totalAmount").value(60.00))
                .andExpect(jsonPath("$.shippingAddress.detailAddress").value("结算路1号"));

        assertEquals(0, cartStore.getItems(userId).size());
        assertEquals(OrderStatus.PENDING_PAYMENT, orderRepository.findAllByUserId(userId,
                org.springframework.data.domain.Pageable.unpaged()).getContent().getFirst().getStatus());
        Inventory inventory = inventoryRepository.findBySkuId(skuId).orElseThrow();
        assertEquals(3, inventory.getAvailableQuantity());
        assertEquals(2, inventory.getReservedQuantity());
    }

    @Test
    void keepsCartWhenOrderTransactionRollsBack() throws Exception {
        cartStore.put(userId, skuId, 6);

        mockMvc.perform(post("/api/users/{userId}/cart/checkout", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressId\":" + addressId + "}"))
                .andExpect(status().isConflict());

        assertEquals(6, cartStore.getItems(userId).get(skuId));
        assertEquals(0, orderRepository.findAllByUserId(userId,
                org.springframework.data.domain.Pageable.unpaged()).getTotalElements());
    }
}
