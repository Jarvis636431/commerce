package com.jarvis.commerce.product;

import com.jarvis.commerce.common.PageResponse;
import com.jarvis.commerce.common.ConflictException;
import com.jarvis.commerce.common.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final SkuRepository skuRepository;

    public ProductService(ProductRepository productRepository, SkuRepository skuRepository) {
        this.productRepository = productRepository;
        this.skuRepository = skuRepository;
    }

    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        Product product = new Product(request.name().trim(), normalizeDescription(request.description()));
        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional(readOnly = true)
    public ProductResponse getById(long id) {
        return ProductResponse.from(findProduct(id));
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
        return ProductResponse.from(product);
    }

    @Transactional
    public ProductResponse takeOffSale(long id) {
        Product product = findProduct(id);
        product.takeOffSale();
        productRepository.flush();
        return ProductResponse.from(product);
    }

    @Transactional
    public void delete(long id) {
        Product product = findProduct(id);
        product.ensureDeletable();
        skuRepository.deleteAllByProductId(id);
        productRepository.delete(product);
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
}
