package com.jarvis.commerce.product;

import com.jarvis.commerce.common.PageResponse;
import com.jarvis.commerce.common.ConflictException;
import com.jarvis.commerce.common.ResourceNotFoundException;
import com.jarvis.commerce.search.ProductSearchIndexer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class ProductService {
    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;
    private final SkuRepository skuRepository;
    private final ProductCacheStore productCacheStore;
    private final ProductSearchIndexer searchIndexer;

    public ProductService(ProductRepository productRepository, SkuRepository skuRepository,
                          ProductCacheStore productCacheStore, ProductSearchIndexer searchIndexer) {
        this.productRepository = productRepository;
        this.skuRepository = skuRepository;
        this.productCacheStore = productCacheStore;
        this.searchIndexer = searchIndexer;
    }

    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        Product product = new Product(request.name().trim(), normalizeDescription(request.description()));
        productRepository.save(product);
        indexAfterCommit(product.getId());
        return ProductResponse.from(product);
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
        indexAfterCommit(id);
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
        indexAfterCommit(id);
        return ProductResponse.from(product);
    }

    @Transactional
    public ProductResponse takeOffSale(long id) {
        Product product = findProduct(id);
        product.takeOffSale();
        productRepository.flush();
        evictAfterCommit(id);
        indexAfterCommit(id);
        return ProductResponse.from(product);
    }

    @Transactional
    public void delete(long id) {
        Product product = findProduct(id);
        product.ensureDeletable();
        skuRepository.deleteAllByProductId(id);
        productRepository.delete(product);
        evictAfterCommit(id);
        deleteIndexAfterCommit(id);
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

    void indexAfterCommit(long id) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    searchIndexer.index(id);
                } catch (RuntimeException exception) {
                    log.error("Failed to update product search index: productId={}", id, exception);
                }
            }
        });
    }

    private void deleteIndexAfterCommit(long id) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    searchIndexer.delete(id);
                } catch (RuntimeException exception) {
                    log.error("Failed to delete product search document: productId={}", id, exception);
                }
            }
        });
    }
}
