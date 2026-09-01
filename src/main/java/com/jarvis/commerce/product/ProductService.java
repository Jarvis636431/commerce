package com.jarvis.commerce.product;

import com.jarvis.commerce.common.PageResponse;
import com.jarvis.commerce.common.ConflictException;
import com.jarvis.commerce.common.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final SkuRepository skuRepository;
    private final ProductCacheStore productCacheStore;

    public ProductService(ProductRepository productRepository, SkuRepository skuRepository,
                          ProductCacheStore productCacheStore) {
        this.productRepository = productRepository;
        this.skuRepository = skuRepository;
        this.productCacheStore = productCacheStore;
    }

    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        Product product = new Product(request.name().trim(), normalizeDescription(request.description()));
        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional(readOnly = true)
    public ProductResponse getById(long id) {
        ProductCacheLookup cached = productCacheStore.get(id);
        if (cached.hit()) {
            if (cached.value() == null) throw notFound(id);
            return cached.value();
        }
        Product product = productRepository.findById(id).orElse(null);
        if (product == null) {
            productCacheStore.putNotFound(id);
            throw notFound(id);
        }
        ProductResponse response = ProductResponse.from(product);
        productCacheStore.put(response);
        return response;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> list(Pageable pageable) {
        Page<Product> products = productRepository.findAll(pageable);
        return PageResponse.from(products, ProductResponse::from);
    }

    @Transactional
    public ProductResponse update(long id, UpdateProductRequest request) {
        Product product = findProduct(id);
        product.update(request.name().trim(), normalizeDescription(request.description()));
        productRepository.flush();
        evictAfterCommit(id);
        return ProductResponse.from(product);
    }

    @Transactional
    public ProductResponse putOnSale(long id) {
        Product product = findProduct(id);
        if (!skuRepository.existsByProductId(id)) {
            throw new ConflictException("A product must have at least one SKU before going on sale");
        }
        product.putOnSale();
        productRepository.flush();
        evictAfterCommit(id);
        return ProductResponse.from(product);
    }

    @Transactional
    public ProductResponse takeOffSale(long id) {
        Product product = findProduct(id);
        product.takeOffSale();
        productRepository.flush();
        evictAfterCommit(id);
        return ProductResponse.from(product);
    }

    @Transactional
    public void delete(long id) {
        Product product = findProduct(id);
        product.ensureDeletable();
        skuRepository.deleteAllByProductId(id);
        productRepository.delete(product);
        evictAfterCommit(id);
    }

    Product findProduct(long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product %d was not found".formatted(id)));
    }

    private String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        return description.trim();
    }

    private ResourceNotFoundException notFound(long id) {
        return new ResourceNotFoundException("Product %d was not found".formatted(id));
    }

    private void evictAfterCommit(long id) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() { productCacheStore.evict(id); }
        });
    }
}
