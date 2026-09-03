package com.jarvis.commerce.search;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Document(indexName = "commerce-products")
@Setting(shards = 1, replicas = 0)
public class ProductSearchDocument {
    @Id
    private String id;

    @Field(type = FieldType.Text)
    private String name;

    @Field(type = FieldType.Text)
    private String description;

    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Text)
    private List<String> skuNames;

    @Field(type = FieldType.Keyword)
    private List<String> skuCodes;

    @Field(type = FieldType.Double)
    private BigDecimal minPrice;

    @Field(type = FieldType.Double)
    private BigDecimal maxPrice;

    @Field(type = FieldType.Keyword, index = false)
    private String mainImageUrl;

    @Field(type = FieldType.Date)
    private OffsetDateTime updatedAt;

    protected ProductSearchDocument() {}

    public ProductSearchDocument(String id, String name, String description, String status,
                                 List<String> skuNames, List<String> skuCodes,
                                 BigDecimal minPrice, BigDecimal maxPrice, String mainImageUrl, OffsetDateTime updatedAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.status = status;
        this.skuNames = skuNames;
        this.skuCodes = skuCodes;
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
        this.mainImageUrl = mainImageUrl;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getStatus() { return status; }
    public List<String> getSkuNames() { return skuNames; }
    public List<String> getSkuCodes() { return skuCodes; }
    public BigDecimal getMinPrice() { return minPrice; }
    public BigDecimal getMaxPrice() { return maxPrice; }
    public String getMainImageUrl() { return mainImageUrl; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
