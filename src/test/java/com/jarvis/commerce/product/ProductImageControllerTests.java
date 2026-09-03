package com.jarvis.commerce.product;

import com.jarvis.commerce.storage.ObjectMetadata;
import com.jarvis.commerce.storage.ObjectStorage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URL;
import java.time.Duration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProductImageControllerTests {
    @Autowired MockMvc mockMvc;
    @Autowired ProductRepository productRepository;

    @Test
    void createsUploadCompletesItAndListsReadyImage() throws Exception {
        Product product = productRepository.save(new Product("Camera", null));
        String body = mockMvc.perform(post("/api/products/{id}/images/uploads", product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"filename":"front.webp","contentType":"image/webp","size":1024}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$['requiredHeaders']['Content-Type']").value("image/webp"))
                .andExpect(jsonPath("$.uploadUrl").isString())
                .andReturn().getResponse().getContentAsString();
        long imageId = Long.parseLong(body.replaceAll(".*\\\"imageId\\\":([0-9]+).*", "$1"));

        mockMvc.perform(post("/api/products/{productId}/images/{imageId}/complete", product.getId(), imageId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(1024))
                .andExpect(jsonPath("$.downloadUrl").isString());

        mockMvc.perform(get("/api/products/{id}/images", product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(imageId));
    }

    @Test
    void rejectsFilenameThatDoesNotMatchMimeType() throws Exception {
        Product product = productRepository.save(new Product("Camera", null));

        mockMvc.perform(post("/api/products/{id}/images/uploads", product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"filename":"script.svg","contentType":"image/png","size":100}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
    }

    @TestConfiguration
    static class StorageTestConfiguration {
        @Bean @Primary
        ObjectStorage testObjectStorage() {
            return new ObjectStorage() {
                @Override public URL presignUpload(String key, Duration ttl) { return url("upload", key); }
                @Override public URL presignDownload(String key, Duration ttl) { return url("download", key); }
                @Override public ObjectMetadata stat(String key) {
                    return new ObjectMetadata(1024, "image/webp", "test-etag");
                }
                @Override public void delete(String key) { }
                private URL url(String action, String key) {
                    try { return URI.create("http://storage.test/" + action + "/" + key).toURL(); }
                    catch (Exception exception) { throw new IllegalStateException(exception); }
                }
            };
        }
    }
}
