package com.backlogtracker.commons.image.domain;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One uploaded, server-compressed image, generically owned by whatever entity in whatever
 * applet attached it (an order's finished-product photo today; a catalog product's photo
 * or an expense's receipt photo later) — deliberately kept in {@code commons} rather than
 * any one applet's package, since multiple applets need the identical upload/compress/
 * serve/delete mechanics and commons is the only package every applet may import from.
 *
 * <p>{@code ownerType}/{@code ownerId} are opaque strings to this layer on purpose — it
 * never interprets them, only stores and returns them. {@code groupId} is the only thing
 * commons itself checks (via {@code GroupService.requireMember}), the same authorization
 * model used everywhere else in the app; the caller is responsible for passing the
 * {@code groupId} that actually owns the entity the image is attached to.
 */
@Document("images")
@CompoundIndex(name = "group_owner", def = "{'groupId': 1, 'ownerType': 1, 'ownerId': 1}")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageAsset {

    @Id
    private String id;

    private String groupId;
    /** e.g. "ordertracker:order" — never interpreted here, just stored and matched on. */
    private String ownerType;
    private String ownerId;

    /** Always "image/jpeg" after server-side compression — see {@code ImageCompressionService}
     *  for why every stored image is normalized to one format. */
    private String contentType;
    private byte[] data;

    /** Display order within one owner's gallery — assigned as (current count) at upload
     *  time, so photos show in the order they were added. */
    private int sequenceOrder;

    private String uploadedByUserId;
    private Instant uploadedAt;
}
