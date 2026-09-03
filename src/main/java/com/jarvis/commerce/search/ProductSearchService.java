package com.jarvis.commerce.search;

import com.jarvis.commerce.common.PageResponse;
import com.jarvis.commerce.common.BadRequestException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@ConditionalOnProperty(name = "commerce.search.enabled", havingValue = "true", matchIfMissing = true)
public class ProductSearchService {
    private final ElasticsearchOperations operations;

    public ProductSearchService(ElasticsearchOperations operations) { this.operations = operations; }

    public PageResponse<ProductSearchResponse> search(String keyword, BigDecimal minPrice, BigDecimal maxPrice,
                                                      ProductSearchSort sort, Pageable pageable) {
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new BadRequestException("minPrice must not be greater than maxPrice");
        }
        var builder = NativeQuery.builder()
                .withQuery(query -> query.bool(bool -> {
                    bool.must(must -> must.multiMatch(multi -> multi.query(keyword.trim())
                            .fields("name^3", "description", "skuNames^2", "skuCodes^2")));
                    bool.filter(filter -> filter.term(term -> term.field("status").value("ON_SALE")));
                    if (minPrice != null) {
                        bool.filter(filter -> filter.range(range -> range.number(number -> number
                                .field("maxPrice").gte(minPrice.doubleValue()))));
                    }
                    if (maxPrice != null) {
                        bool.filter(filter -> filter.range(range -> range.number(number -> number
                                .field("minPrice").lte(maxPrice.doubleValue()))));
                    }
                    return bool;
                }))
                .withPageable(PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()))
                .withHighlightQuery(new HighlightQuery(new Highlight(List.of(
                        new HighlightField("name"), new HighlightField("description"),
                        new HighlightField("skuNames"))), ProductSearchDocument.class));
        switch (sort) {
            case PRICE_ASC -> builder.withSort(option -> option.field(field -> field.field("minPrice")
                    .order(co.elastic.clients.elasticsearch._types.SortOrder.Asc)));
            case PRICE_DESC -> builder.withSort(option -> option.field(field -> field.field("minPrice")
                    .order(co.elastic.clients.elasticsearch._types.SortOrder.Desc)));
            case NEWEST -> builder.withSort(option -> option.field(field -> field.field("updatedAt")
                    .order(co.elastic.clients.elasticsearch._types.SortOrder.Desc)));
            case RELEVANCE -> { }
        }
        var hits = operations.search(builder.build(), ProductSearchDocument.class);
        List<ProductSearchResponse> content = hits.getSearchHits().stream().map(ProductSearchResponse::from).toList();
        int totalPages = (int) Math.ceil((double) hits.getTotalHits() / pageable.getPageSize());
        return new PageResponse<>(content, pageable.getPageNumber(), pageable.getPageSize(),
                hits.getTotalHits(), totalPages);
    }
}
