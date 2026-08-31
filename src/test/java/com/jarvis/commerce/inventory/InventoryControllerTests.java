package com.jarvis.commerce.inventory;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class InventoryControllerTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ProductRepository productRepository;
    @Autowired private SkuRepository skuRepository;

    private Long skuId;

    @BeforeEach
    void createSku() {
        Product product = productRepository.save(new Product("Course", null));
        Sku sku = skuRepository.save(new Sku(product, "SKU-" + System.nanoTime(), "Standard",
                new BigDecimal("99.90")));
        skuId = sku.getId();
    }

    @Test
    void runsReservationAndConfirmationFlow() throws Exception {
        initialize(10);

        mockMvc.perform(post("/api/skus/{skuId}/inventory/reserve", skuId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableQuantity").value(7))
                .andExpect(jsonPath("$.reservedQuantity").value(3));

        mockMvc.perform(post("/api/skus/{skuId}/inventory/confirm", skuId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableQuantity").value(7))
                .andExpect(jsonPath("$.reservedQuantity").value(0));
    }

    @Test
    void releasesReservedStock() throws Exception {
        initialize(10);
        postQuantity("reserve", 4);

        mockMvc.perform(post("/api/skus/{skuId}/inventory/release", skuId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableQuantity").value(10))
                .andExpect(jsonPath("$.reservedQuantity").value(0));
    }

    @Test
    void rejectsReservationWhenStockIsInsufficient() throws Exception {
        initialize(2);

        mockMvc.perform(post("/api/skus/{skuId}/inventory/reserve", skuId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":3}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Business rule conflict"));
    }

    @Test
    void rejectsNonPositiveQuantity() throws Exception {
        initialize(2);

        mockMvc.perform(post("/api/skus/{skuId}/inventory/increase", skuId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getsInventory() throws Exception {
        initialize(5);

        mockMvc.perform(get("/api/skus/{skuId}/inventory", skuId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skuId").value(skuId))
                .andExpect(jsonPath("$.availableQuantity").value(5));
    }

    private void initialize(int quantity) throws Exception {
        mockMvc.perform(put("/api/skus/{skuId}/inventory", skuId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":" + quantity + "}"))
                .andExpect(status().isCreated());
    }

    private void postQuantity(String operation, int quantity) throws Exception {
        mockMvc.perform(post("/api/skus/{skuId}/inventory/{operation}", skuId, operation)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":" + quantity + "}"))
                .andExpect(status().isOk());
    }
}
