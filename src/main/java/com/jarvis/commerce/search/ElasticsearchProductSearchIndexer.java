package com.jarvis.commerce.search;

import com.jarvis.commerce.product.Product;
import com.jarvis.commerce.product.ProductRepository;
import com.jarvis.commerce.product.Sku;
import com.jarvis.commerce.product.SkuRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.util.List;

@Component
@ConditionalOnProperty(name = "commerce.search.enabled", havingValue = "true", matchIfMissing = true)
public class ElasticsearchProductSearchIndexer implements ProductSearchIndexer {
    private final ProductRepository productRepository;
    private final SkuRepository skuRepository;
    private final ProductSearchRepository searchRepository;
    private final ProductSearchDocumentMapper mapper;

    public ElasticsearchProductSearchIndexer(ProductRepository productRepository, SkuRepository skuRepository,
                                             ProductSearchRepository searchRepository,
                                             ProductSearchDocumentMapper mapper) {
        this.productRepository = productRepository;
        this.skuRepository = skuRepository;
        this.searchRepository = searchRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public void index(long productId) {
        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) {
            searchRepository.deleteById(Long.toString(productId));
            return;
        }
        List<Sku> skus = skuRepository.findAllByProductIdOrderByIdAsc(productId);
        searchRepository.save(mapper.map(productId, product, skus));
    }

    @Override
    public void delete(long productId) {
        searchRepository.deleteById(Long.toString(productId));
    }

    @Override
    @Transactional(readOnly = true)
    public long rebuild() {
        searchRepository.deleteAll();
        long indexed = 0;
        Page<Product> page;
        int number = 0;
        do {
            page = productRepository.findAll(PageRequest.of(number++, 200));
            for (Product product : page) {
                List<Sku> skus = skuRepository.findAllByProductIdOrderByIdAsc(product.getId());
                searchRepository.save(mapper.map(product.getId(), product, skus));
                indexed++;
            }
        } while (page.hasNext());
        return indexed;
    }

}
