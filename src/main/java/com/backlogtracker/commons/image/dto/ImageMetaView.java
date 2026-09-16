package com.backlogtracker.commons.image.dto;

import java.time.Instant;

import com.backlogtracker.commons.image.domain.ImageAsset;

/** Metadata only — never the raw bytes, so listing a gallery stays cheap regardless of
 *  how many images it has. The actual bytes are fetched one at a time via the raw-image
 *  endpoint (an {@code <img src>} pointed straight at it). */
public record ImageMetaView(String id, int sequenceOrder, String uploadedByUserId, Instant uploadedAt, String contentType) {

    public static ImageMetaView of(ImageAsset a) {
        return new ImageMetaView(a.getId(), a.getSequenceOrder(), a.getUploadedByUserId(), a.getUploadedAt(), a.getContentType());
    }
}
