package com.jarvis.commerce.cart;

import com.jarvis.commerce.inventory.Inventory;
import com.jarvis.commerce.inventory.InventoryRepository;
import com.jarvis.commerce.product.Product;
import com.jarvis.commerce.product.ProductRepository;
import com.jarvis.commerce.product.Sku;
import com.jarvis.commerce.product.SkuRepository;
import com.jarvis.commerce.user.User;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CartControllerTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private SkuRepository skuRepository;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private CartStore cartStore;

    private User user;
    private Product product;
    private Sku sku;

    @BeforeEach
    void setUp() {
        user = userRepository.save(new User("cart-" + System.nanoTime(),
                "cart-" + System.nanoTime() + "@example.com", null));
        product = productRepository.save(new Product("Redis Course", null));
        sku = skuRepository.save(new Sku(product, "CART-" + System.nanoTime(), "Standard",
                new BigDecimal("25.50")));
        inventoryRepository.save(new Inventory(sku, 5));
        product.putOnSale();
        productRepository.flush();
        cartStore.clear(user.getId());
    }

    @Test
    void addsSameSkuAtomicallyAndEnrichesCurrentData() throws Exception {
        add(2).andExpect(status().isOk());
        add(1).andExpect(status().isOk())
                .andExpect(jsonPath("$.itemCount").value(1))
                .andExpect(jsonPath("$.totalQuantity").value(3))
                .andExpect(jsonPath("$.totalAmount").value(76.50))
                .andExpect(jsonPath("$.items[0].skuId").value(sku.getId()))
                .andExpect(jsonPath("$.items[0].availableQuantity").value(5))
                .andExpect(jsonPath("$.items[0].purchasable").value(true));
    }

    @Test
    void updatesAndRemovesItem() throws Exception {
        add(1).andExpect(status().isOk());

        mockMvc.perform(put("/api/users/{userId}/cart/items/{skuId}", user.getId(), sku.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalQuantity").value(4));

        mockMvc.perform(delete("/api/users/{userId}/cart/items/{skuId}", user.getId(), sku.getId()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/users/{userId}/cart", user.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemCount").value(0));
    }

    @Test
    void reflectsInsufficientStockWithoutReservingIt() throws Exception {
        add(6).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].purchasable").value(false))
                .andExpect(jsonPath("$.totalAmount").value(0));

        org.junit.jupiter.api.Assertions.assertEquals(5,
                inventoryRepository.findBySkuId(sku.getId()).orElseThrow().getAvailableQuantity());
    }

    @Test
    void rejectsInvalidProductsUsersAndQuantityOverflow() throws Exception {
        add(99).andExpect(status().isOk());
        add(1).andExpect(status().isConflict());

        mockMvc.perform(post("/api/users/{userId}/cart/items", user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skuId\":" + sku.getId() + ",\"quantity\":0}"))
                .andExpect(status().isBadRequest());

        user.disable();
        userRepository.flush();
        mockMvc.perform(get("/api/users/{userId}/cart", user.getId()))
                .andExpect(status().isConflict());
    }

    private org.springframework.test.web.servlet.ResultActions add(int quantity) throws Exception {
        return mockMvc.perform(post("/api/users/{userId}/cart/items", user.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"skuId\":" + sku.getId() + ",\"quantity\":" + quantity + "}"));
    }
}
