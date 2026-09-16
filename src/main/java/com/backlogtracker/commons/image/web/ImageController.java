package com.backlogtracker.commons.image.web;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.backlogtracker.commons.image.domain.ImageAsset;
import com.backlogtracker.commons.image.dto.ImageMetaView;
import com.backlogtracker.commons.image.service.ImageService;
import com.backlogtracker.commons.security.AuthUser;
import com.backlogtracker.commons.security.RequiresUser;

import lombok.RequiredArgsConstructor;

/** Generic gallery endpoints, reused by every applet — see {@link ImageAsset}. */
@RestController
@RequestMapping("/api/images/{groupId}/{ownerType}/{ownerId}")
@RequiresUser
@RequiredArgsConstructor
public class ImageController {

    private final ImageService imageService;

    @GetMapping
    public List<ImageMetaView> list(@PathVariable String groupId, @PathVariable String ownerType,
                                    @PathVariable String ownerId, @AuthenticationPrincipal AuthUser actor) {
        return imageService.list(groupId, actor.id(), ownerType, ownerId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ImageMetaView upload(@PathVariable String groupId, @PathVariable String ownerType,
                                @PathVariable String ownerId, @RequestParam MultipartFile file,
                                @AuthenticationPrincipal AuthUser actor) {
        return imageService.upload(groupId, actor.id(), ownerType, ownerId, file);
    }

    /** Raw bytes, not JSON — an {@code <img src>} points straight at this. */
    @GetMapping("/{imageId}/raw")
    public ResponseEntity<byte[]> raw(@PathVariable String groupId, @PathVariable String ownerType,
                                      @PathVariable String ownerId, @PathVariable String imageId,
                                      @AuthenticationPrincipal AuthUser actor) {
        ImageAsset image = imageService.getRaw(groupId, actor.id(), imageId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.getContentType()))
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=31536000, immutable")
                .body(image.getData());
    }

    @DeleteMapping("/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String groupId, @PathVariable String ownerType, @PathVariable String ownerId,
                       @PathVariable String imageId, @AuthenticationPrincipal AuthUser actor) {
        imageService.delete(groupId, actor.id(), imageId);
    }
}
