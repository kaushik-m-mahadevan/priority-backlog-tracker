package com.backlogtracker.commons.image.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Normalizes every uploaded image to a JPEG under a target byte size (design decision:
 * compress to roughly 1MB, accept up to 5MB raw) — a phone camera photo can be 10x that,
 * and storing it uncompressed in Mongo would make a 6-image gallery cost tens of MB per
 * order. Quality reduction is tried first (cheaper, less visible loss); if the image still
 * doesn't fit at the quality floor, dimensions are scaled down and quality reduction runs
 * again. Always re-encodes as JPEG regardless of the source format, including flattening
 * transparency onto white — a finished-product photo gallery has no real use for alpha,
 * and normalizing the stored format keeps serving/storage code simple (one content type).
 */
@Service
public class ImageCompressionService {

    private static final long TARGET_BYTES = 1_000_000;
    private static final float QUALITY_FLOOR = 0.3f;
    private static final float QUALITY_STEP = 0.1f;
    private static final int MAX_SCALE_ATTEMPTS = 3;
    private static final double SCALE_FACTOR = 0.75;

    public byte[] compressToJpeg(byte[] original) {
        BufferedImage image = read(original);
        BufferedImage current = image;

        for (int scaleAttempt = 0; scaleAttempt <= MAX_SCALE_ATTEMPTS; scaleAttempt++) {
            byte[] best = null;
            for (float quality = 0.85f; quality >= QUALITY_FLOOR - 1e-6; quality -= QUALITY_STEP) {
                byte[] encoded = encodeJpeg(current, quality);
                best = encoded;
                if (encoded.length <= TARGET_BYTES) {
                    return encoded;
                }
            }
            if (scaleAttempt == MAX_SCALE_ATTEMPTS) {
                // Ran out of scale-downs — return the smallest we managed rather than fail
                // the upload outright over a target that's a soft goal, not a hard limit.
                return best;
            }
            current = scale(current, SCALE_FACTOR);
        }
        throw new IllegalStateException("unreachable");
    }

    private BufferedImage read(byte[] bytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unrecognized image format");
            }
            return image;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read image file");
        }
    }

    private BufferedImage scale(BufferedImage source, double factor) {
        int width = Math.max(1, (int) (source.getWidth() * factor));
        int height = Math.max(1, (int) (source.getHeight() * factor));
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.drawImage(source, 0, 0, width, height, null);
        g.dispose();
        return scaled;
    }

    private byte[] encodeJpeg(BufferedImage source, float quality) {
        BufferedImage rgb = flattenToRgb(source);
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IllegalStateException("No JPEG writer available on this JVM");
        }
        ImageWriter writer = writers.next();
        try {
            ImageWriteParam params = writer.getDefaultWriteParam();
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(quality);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(out)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(rgb, null, null), params);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to encode JPEG", e);
        } finally {
            writer.dispose();
        }
    }

    /** JPEG has no alpha channel — flattens any transparency onto white first, since a
     *  TYPE_INT_ARGB source otherwise produces a corrupt/unreadable JPEG from some writers. */
    private BufferedImage flattenToRgb(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_RGB && !source.getColorModel().hasAlpha()) {
            return source;
        }
        BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, source.getWidth(), source.getHeight());
        g.drawImage(source, 0, 0, null);
        g.dispose();
        return rgb;
    }
}
