package com.backlogtracker.commons.image.service;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.backlogtracker.commons.group.service.GroupService;
import com.backlogtracker.commons.image.domain.ImageAsset;
import com.backlogtracker.commons.image.dto.ImageMetaView;
import com.backlogtracker.commons.image.repository.ImageAssetRepository;

import lombok.RequiredArgsConstructor;

/**
 * Generic image upload/list/serve/delete, shared by every applet that attaches photos to
 * something (Order Tracker's finished-product gallery today; Product Catalog and Finance
 * Tracker's invoice attachment later) — see {@link ImageAsset} for why this lives in
 * commons rather than any one applet's package.
 */
@Service
@RequiredArgsConstructor
public class ImageService {

    /** Accepted raw upload size (design decision) — well under Tomcat's own multipart cap
     *  (see application.yml) so an over-limit upload gets this clear message instead of a
     *  generic container-level rejection. */
    private static final long MAX_UPLOAD_BYTES = 5L * 1024 * 1024;
    /** Per (groupId, ownerType, ownerId) gallery (design decision). */
    private static final int MAX_IMAGES_PER_OWNER = 6;
    private static final Set<String> ACCEPTED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final ImageAssetRepository repository;
    private final ImageCompressionService compressionService;
    private final GroupService groupService;
    private final Clock clock;

    public List<ImageMetaView> list(String groupId, String userId, String ownerType, String ownerId) {
        groupService.requireMember(groupId, userId);
        return repository.findByGroupIdAndOwnerTypeAndOwnerIdOrderBySequenceOrderAsc(groupId, ownerType, ownerId)
                .stream().map(ImageMetaView::of).toList();
    }

    public ImageMetaView upload(String groupId, String userId, String ownerType, String ownerId, MultipartFile file) {
        groupService.requireMember(groupId, userId);
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No file uploaded");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image must be 5MB or smaller");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ACCEPTED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only JPEG, PNG, or WEBP images are accepted");
        }
        long existing = repository.countByGroupIdAndOwnerTypeAndOwnerId(groupId, ownerType, ownerId);
        if (existing >= MAX_IMAGES_PER_OWNER) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This gallery already has the maximum of " + MAX_IMAGES_PER_OWNER + " images — remove one first");
        }

        byte[] raw;
        try {
            raw = file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read the uploaded file");
        }
        byte[] compressed = compressionService.compressToJpeg(raw);

        ImageAsset saved = repository.save(ImageAsset.builder()
                .groupId(groupId)
                .ownerType(ownerType)
                .ownerId(ownerId)
                .contentType("image/jpeg")
                .data(compressed)
                .sequenceOrder((int) existing)
                .uploadedByUserId(userId)
                .uploadedAt(Instant.now(clock))
                .build());
        return ImageMetaView.of(saved);
    }

    /** Returns the full entity (including bytes) — only the raw-serving controller method
     *  should ever call this; every other read goes through {@link #list}. */
    public ImageAsset getRaw(String groupId, String userId, String imageId) {
        groupService.requireMember(groupId, userId);
        ImageAsset image = repository.findById(imageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found"));
        if (!groupId.equals(image.getGroupId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found");
        }
        return image;
    }

    public void delete(String groupId, String userId, String imageId) {
        ImageAsset image = getRaw(groupId, userId, imageId);
        repository.deleteById(image.getId());
    }
}
