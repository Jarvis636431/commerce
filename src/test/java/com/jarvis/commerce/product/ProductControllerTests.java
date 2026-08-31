package com.jarvis.commerce.product;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProductControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SkuRepository skuRepository;

    @Test
    void createsProduct() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Java Backend Course",
                                  "description": "Learn Spring Data JPA"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Java Backend Course"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void rejectsBlankName() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": " "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
    }

    @Test
    void returnsNotFoundForUnknownProduct() throws Exception {
        mockMvc.perform(get("/api/products/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"));
    }

    @Test
    void updatesProduct() throws Exception {
        Product product = productRepository.save(new Product("Old name", null));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/products/{id}", product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "New name", "description": "New description"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New name"))
                .andExpect(jsonPath("$.description").value("New description"));
    }

    @Test
    void requiresSkuBeforePuttingProductOnSale() throws Exception {
        Product product = productRepository.save(new Product("Course", null));

        mockMvc.perform(post("/api/products/{id}/on-sale", product.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Business rule conflict"));
    }

    @Test
    void createsSkuAndPutsProductOnSale() throws Exception {
        Product product = productRepository.save(new Product("Course", null));

        mockMvc.perform(post("/api/products/{id}/skus", product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "COURSE-001", "name": "Standard", "price": 99.90}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("COURSE-001"))
                .andExpect(jsonPath("$.price").value(99.90));

        mockMvc.perform(post("/api/products/{id}/on-sale", product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ON_SALE"));
    }

    @Test
    void preventsEditingOnSaleProduct() throws Exception {
        Product product = productRepository.save(new Product("Course", null));
        skuRepository.save(new Sku(product, "COURSE-002", "Standard", new java.math.BigDecimal("99.90")));
        product.putOnSale();
        productRepository.flush();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/products/{id}", product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Changed"}
                                """))
                .andExpect(status().isConflict());
    }
}
