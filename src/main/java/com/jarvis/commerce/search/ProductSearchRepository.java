package com.jarvis.commerce.search;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ProductSearchRepository extends ElasticsearchRepository<ProductSearchDocument, String> {
    @Query("""
            {
              "bool": {
                "must": [{
                  "multi_match": {
                    "query": "?0",
                    "fields": ["name^3", "description", "skuNames^2", "skuCodes^2"]
                  }
                }],
                "filter": [{"term": {"status": "ON_SALE"}}]
              }
            }
            """)
    Page<ProductSearchDocument> searchOnSale(String keyword, Pageable pageable);
}
