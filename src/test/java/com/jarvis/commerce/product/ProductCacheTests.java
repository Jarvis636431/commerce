package com.jarvis.commerce.product;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductCacheTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductCacheStore cacheStore;

    @Test
    void cachesDetailAndEvictsOnlyAfterCommittedUpdate() throws Exception {
        Product product = productRepository.save(new Product("Cache Course", "old"));
        cacheStore.evict(product.getId());

        mockMvc.perform(get("/api/products/{id}", product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cache Course"));
        assertTrue(cacheStore.get(product.getId()).hit());

        mockMvc.perform(put("/api/products/{id}", product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated Course\",\"description\":\"new\"}"))
                .andExpect(status().isOk());
        assertFalse(cacheStore.get(product.getId()).hit());

        mockMvc.perform(get("/api/products/{id}", product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Course"));
    }

    @Test
    void cachesNotFoundResultToPreventPenetration() throws Exception {
        long missingId = Long.MAX_VALUE;
        cacheStore.evict(missingId);

        mockMvc.perform(get("/api/products/{id}", missingId)).andExpect(status().isNotFound());

        ProductCacheLookup cached = cacheStore.get(missingId);
        assertTrue(cached.hit());
        assertNull(cached.value());
    }
}
