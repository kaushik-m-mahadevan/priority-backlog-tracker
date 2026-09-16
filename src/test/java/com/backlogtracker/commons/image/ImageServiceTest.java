package com.backlogtracker.commons.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.backlogtracker.commons.group.domain.Group;
import com.backlogtracker.commons.group.repository.GroupRepository;
import com.backlogtracker.commons.group.service.GroupService;
import com.backlogtracker.commons.image.domain.ImageAsset;
import com.backlogtracker.commons.image.dto.ImageMetaView;
import com.backlogtracker.commons.image.repository.ImageAssetRepository;
import com.backlogtracker.commons.image.service.ImageService;
import com.backlogtracker.commons.user.domain.AccountStatus;
import com.backlogtracker.commons.user.domain.Role;
import com.backlogtracker.commons.user.domain.User;
import com.backlogtracker.commons.user.repository.UserRepository;

@SpringBootTest
class ImageServiceTest {

    @Autowired GroupService groupService;
    @Autowired ImageService imageService;
    @Autowired ImageAssetRepository images;
    @Autowired GroupRepository groups;
    @Autowired UserRepository users;

    private String userId;
    private Group group;
    private static final String OWNER_TYPE = "order";
    private static final String OWNER_ID = "order-1";

    @BeforeEach
    void setUp() {
        userId = users.save(User.builder().name("Image Tester").email("image-tester@x.test")
                .passwordHash("x").role(Role.USER).status(AccountStatus.ACTIVE).handle("imagetester").build()).getId();
        group = groupService.create("Image Test Business", userId, Group.APPLET_ORDER_TRACKER);
    }

    @AfterEach
    void cleanUp() {
        images.findByGroupIdAndOwnerTypeAndOwnerIdOrderBySequenceOrderAsc(group.getId(), OWNER_TYPE, OWNER_ID)
                .forEach(i -> images.deleteById(i.getId()));
        groups.deleteById(group.getId());
        users.deleteById(userId);
    }

    private static byte[] pngBytes(int width, int height) throws Exception {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                img.setRGB(x, y, (x * 31 + y * 17) & 0xFFFFFF); // noisy pattern, not flat-compressible
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private MockMultipartFile pngFile(int width, int height) throws Exception {
        return new MockMultipartFile("file", "test.png", "image/png", pngBytes(width, height));
    }

    @Test
    void uploadsAndCompressesAnImageToJpeg() throws Exception {
        ImageMetaView uploaded = imageService.upload(group.getId(), userId, OWNER_TYPE, OWNER_ID, pngFile(200, 200));

        assertThat(uploaded.id()).isNotBlank();
        assertThat(uploaded.sequenceOrder()).isEqualTo(0);

        ImageAsset raw = imageService.getRaw(group.getId(), userId, uploaded.id());
        assertThat(raw.getContentType()).isEqualTo("image/jpeg");
        assertThat(raw.getData()).isNotEmpty();
    }

    @Test
    void listsImagesInUploadOrder() throws Exception {
        imageService.upload(group.getId(), userId, OWNER_TYPE, OWNER_ID, pngFile(50, 50));
        imageService.upload(group.getId(), userId, OWNER_TYPE, OWNER_ID, pngFile(50, 50));

        List<ImageMetaView> list = imageService.list(group.getId(), userId, OWNER_TYPE, OWNER_ID);

        assertThat(list).hasSize(2);
        assertThat(list.get(0).sequenceOrder()).isEqualTo(0);
        assertThat(list.get(1).sequenceOrder()).isEqualTo(1);
    }

    @Test
    void rejectsAnUnsupportedContentType() {
        MockMultipartFile textFile = new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes());

        assertThatThrownBy(() -> imageService.upload(group.getId(), userId, OWNER_TYPE, OWNER_ID, textFile))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("JPEG, PNG, WEBP, or PDF");
    }

    @Test
    void uploadsAPdfAsIsWithoutCompression() {
        byte[] pdfBytes = "%PDF-1.4 fake pdf bytes for a test".getBytes();
        MockMultipartFile pdfFile = new MockMultipartFile("file", "invoice.pdf", "application/pdf", pdfBytes);

        ImageMetaView uploaded = imageService.upload(group.getId(), userId, OWNER_TYPE, OWNER_ID, pdfFile);

        assertThat(uploaded.contentType()).isEqualTo("application/pdf");
        ImageAsset raw = imageService.getRaw(group.getId(), userId, uploaded.id());
        assertThat(raw.getContentType()).isEqualTo("application/pdf");
        assertThat(raw.getData()).isEqualTo(pdfBytes);
    }

    @Test
    void rejectsAFileOverFiveMegabytes() {
        MockMultipartFile tooBig = new MockMultipartFile("file", "big.jpg", "image/jpeg", new byte[6 * 1024 * 1024]);

        assertThatThrownBy(() -> imageService.upload(group.getId(), userId, OWNER_TYPE, OWNER_ID, tooBig))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("5MB");
    }

    @Test
    void rejectsAnUploadOnceTheGalleryHasSixImages() throws Exception {
        for (int i = 0; i < 6; i++) {
            imageService.upload(group.getId(), userId, OWNER_TYPE, OWNER_ID, pngFile(20, 20));
        }

        assertThatThrownBy(() -> imageService.upload(group.getId(), userId, OWNER_TYPE, OWNER_ID, pngFile(20, 20)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("maximum");
    }

    @Test
    void deletingAnImageRemovesItFromTheList() throws Exception {
        ImageMetaView uploaded = imageService.upload(group.getId(), userId, OWNER_TYPE, OWNER_ID, pngFile(30, 30));

        imageService.delete(group.getId(), userId, uploaded.id());

        assertThat(imageService.list(group.getId(), userId, OWNER_TYPE, OWNER_ID)).isEmpty();
    }

    @Test
    void aNonMemberCannotUploadOrListOrDelete() throws Exception {
        String outsiderId = users.save(User.builder().name("Outsider").email("image-outsider@x.test")
                .passwordHash("x").role(Role.USER).status(AccountStatus.ACTIVE).handle("imageoutsider").build()).getId();
        try {
            assertThatThrownBy(() -> imageService.upload(group.getId(), outsiderId, OWNER_TYPE, OWNER_ID, pngFile(20, 20)))
                    .isInstanceOf(ResponseStatusException.class);
            assertThatThrownBy(() -> imageService.list(group.getId(), outsiderId, OWNER_TYPE, OWNER_ID))
                    .isInstanceOf(ResponseStatusException.class);
        } finally {
            users.deleteById(outsiderId);
        }
    }
}
