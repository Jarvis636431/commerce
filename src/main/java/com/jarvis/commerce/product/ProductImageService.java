package com.jarvis.commerce.product;

import com.jarvis.commerce.common.BadRequestException;
import com.jarvis.commerce.common.ResourceNotFoundException;
import com.jarvis.commerce.storage.ObjectMetadata;
import com.jarvis.commerce.storage.ObjectStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.unit.DataSize;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class ProductImageService {
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp",
            "image/gif", ".gif");

    private final ProductRepository productRepository;
    private final ProductImageRepository imageRepository;
    private final ObjectStorage storage;
    private final Duration uploadUrlTtl;
    private final Duration downloadUrlTtl;
    private final long maxImageSize;

    public ProductImageService(ProductRepository productRepository, ProductImageRepository imageRepository,
                               ObjectStorage storage,
                               @Value("${commerce.storage.upload-url-ttl:PT15M}") Duration uploadUrlTtl,
                               @Value("${commerce.storage.download-url-ttl:PT15M}") Duration downloadUrlTtl,
                               @Value("${commerce.storage.max-image-size:10MB}") DataSize maxImageSize) {
        this.productRepository = productRepository;
        this.imageRepository = imageRepository;
        this.storage = storage;
        this.uploadUrlTtl = uploadUrlTtl;
        this.downloadUrlTtl = downloadUrlTtl;
        this.maxImageSize = maxImageSize.toBytes();
    }

    public ProductImageUploadResponse createUpload(long productId, CreateProductImageUploadRequest request) {
        requireProduct(productId);
        String contentType = normalizeAndValidate(request.filename(), request.contentType(), request.size());
        String objectKey = "products/" + productId + "/" + UUID.randomUUID() + EXTENSIONS.get(contentType);
        ProductImage image = imageRepository.save(new ProductImage(productId, objectKey,
                request.filename().trim(), contentType, request.size()));
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plus(uploadUrlTtl);
        return new ProductImageUploadResponse(image.getId(), objectKey,
                storage.presignUpload(objectKey, uploadUrlTtl), Map.of("Content-Type", contentType), expiresAt);
    }

    public ProductImageResponse completeUpload(long productId, long imageId) {
        ProductImage image = requireImage(productId, imageId);
        if (image.getStatus() == ProductImageStatus.READY) return response(image);

        ObjectMetadata metadata = storage.stat(image.getObjectKey());
        if (metadata.size() != image.getDeclaredSize() || metadata.size() > maxImageSize
                || !image.getContentType().equalsIgnoreCase(metadata.contentType())) {
            storage.delete(image.getObjectKey());
            imageRepository.delete(image);
            throw new BadRequestException("Uploaded object does not match the declared image type or size");
        }
        image.markReady(metadata.size(), metadata.etag());
        return response(imageRepository.save(image));
    }

    public List<ProductImageResponse> list(long productId) {
        requireProduct(productId);
        return imageRepository.findByProductIdAndStatusOrderById(productId, ProductImageStatus.READY)
                .stream().map(this::response).toList();
    }

    public void delete(long productId, long imageId) {
        ProductImage image = requireImage(productId, imageId);
        storage.delete(image.getObjectKey());
        imageRepository.delete(image);
    }

    private ProductImageResponse response(ProductImage image) {
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plus(downloadUrlTtl);
        return new ProductImageResponse(image.getId(), image.getProductId(), image.getOriginalFilename(),
                image.getContentType(), image.getActualSize(), image.getEtag(),
                storage.presignDownload(image.getObjectKey(), downloadUrlTtl), expiresAt, image.getCreatedAt());
    }

    private String normalizeAndValidate(String filename, String rawContentType, long size) {
        String contentType = rawContentType.trim().toLowerCase(Locale.ROOT);
        String extension = EXTENSIONS.get(contentType);
        if (extension == null) throw new BadRequestException("Only JPEG, PNG, WebP and GIF images are supported");
        String lowerFilename = filename.trim().toLowerCase(Locale.ROOT);
        boolean extensionMatches = lowerFilename.endsWith(extension)
                || (contentType.equals("image/jpeg") && lowerFilename.endsWith(".jpeg"));
        if (!extensionMatches) throw new BadRequestException("Filename extension does not match contentType");
        if (size > maxImageSize) throw new BadRequestException("Image exceeds maximum size of " + maxImageSize + " bytes");
        return contentType;
    }

    private void requireProduct(long productId) {
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Product not found: " + productId);
        }
    }

    private ProductImage requireImage(long productId, long imageId) {
        return imageRepository.findByIdAndProductId(imageId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product image not found: " + imageId));
    }
}
