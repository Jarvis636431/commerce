package com.jarvis.commerce.product;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.net.URI;

@RestController
@RequestMapping("/api/products/{productId}/images")
public class ProductImageController {
    private final ProductImageService service;

    public ProductImageController(ProductImageService service) { this.service = service; }

    @PostMapping("/uploads")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductImageUploadResponse createUpload(@PathVariable long productId,
                                                   @Valid @RequestBody CreateProductImageUploadRequest request) {
        return service.createUpload(productId, request);
    }

    @PostMapping("/{imageId}/complete")
    public ProductImageResponse completeUpload(@PathVariable long productId, @PathVariable long imageId) {
        return service.completeUpload(productId, imageId);
    }

    @GetMapping
    public List<ProductImageResponse> list(@PathVariable long productId) { return service.list(productId); }

    @PutMapping("/{imageId}")
    public ProductImageResponse updateDisplay(@PathVariable long productId, @PathVariable long imageId,
                                              @Valid @RequestBody UpdateProductImageRequest request) {
        return service.updateDisplay(productId, imageId, request);
    }

    @GetMapping("/{imageId}/content")
    public ResponseEntity<Void> content(@PathVariable long productId, @PathVariable long imageId) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(service.download(productId, imageId).toString())).build();
    }

    @DeleteMapping("/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long productId, @PathVariable long imageId) {
        service.delete(productId, imageId);
    }
}
