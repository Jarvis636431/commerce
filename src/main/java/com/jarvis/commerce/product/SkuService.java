package com.jarvis.commerce.product;

import com.jarvis.commerce.common.ConflictException;
import com.jarvis.commerce.common.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SkuService {

    private final SkuRepository skuRepository;
    private final ProductService productService;

    public SkuService(SkuRepository skuRepository, ProductService productService) {
        this.skuRepository = skuRepository;
        this.productService = productService;
    }

    @Transactional
    public SkuResponse create(long productId, CreateSkuRequest request) {
        Product product = productService.findProduct(productId);
        product.ensureSkuEditable();
        String code = request.code().trim();
        if (skuRepository.existsByCode(code)) {
            throw new ConflictException("SKU code %s already exists".formatted(code));
        }
        Sku sku = new Sku(product, code, request.name().trim(), request.price());
        return SkuResponse.from(skuRepository.save(sku));
    }

    @Transactional(readOnly = true)
    public List<SkuResponse> list(long productId) {
        productService.findProduct(productId);
        return skuRepository.findAllByProductIdOrderByIdAsc(productId).stream().map(SkuResponse::from).toList();
    }

    @Transactional
    public SkuResponse update(long productId, long skuId, UpdateSkuRequest request) {
        Sku sku = findSku(productId, skuId);
        sku.update(request.name().trim(), request.price());
        skuRepository.flush();
        return SkuResponse.from(sku);
    }

    @Transactional
    public void delete(long productId, long skuId) {
        Sku sku = findSku(productId, skuId);
        sku.getProduct().ensureSkuEditable();
        skuRepository.delete(sku);
    }

    private Sku findSku(long productId, long skuId) {
        Sku sku = skuRepository.findById(skuId)
                .orElseThrow(() -> new ResourceNotFoundException("SKU %d was not found".formatted(skuId)));
        if (!sku.getProduct().getId().equals(productId)) {
            throw new ResourceNotFoundException("SKU %d does not belong to product %d".formatted(skuId, productId));
        }
        return sku;
    }
}
