package com.housekeeping.management.service;

import com.housekeeping.management.repository.BookingImageRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class EvidenceStorageService {
    private static final long MAX_FILE_BYTES = 5L * 1024 * 1024;
    private static final ZoneOffset BUSINESS_OFFSET = ZoneOffset.ofHours(7);

    private final BookingImageRepository bookingImages;
    private final TransactionTemplate transactionTemplate;
    private final Path storageRoot;

    public EvidenceStorageService(
            BookingImageRepository bookingImages,
            PlatformTransactionManager transactionManager,
            @Value("${app.evidence.storage-root:./var/evidence}") String storageRoot) {
        this.bookingImages = bookingImages;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
    }

    public Long upload(Long bookingId, Long staffId, MultipartFile upload) {
        ValidatedImage image = validate(upload);
        Path temporaryFile = null;
        Path finalFile = null;
        try {
            Path temporaryDirectory = storageRoot.resolve(".tmp");
            Files.createDirectories(temporaryDirectory);
            temporaryFile = Files.createTempFile(temporaryDirectory, "evidence-", ".upload");
            Files.write(temporaryFile, image.bytes());

            Path tempForCleanup = temporaryFile;
            finalFile = storageRoot.resolve(UUID.randomUUID() + image.extension()).normalize();
            if (!finalFile.getParent().equals(storageRoot)) {
                throw new IllegalStateException("Generated evidence path escaped the configured storage root");
            }
            Path finalForCleanup = finalFile;

            Long saved = transactionTemplate.execute(status -> {
                BookingImageRepository.BookingAccess booking = bookingImages.lockBooking(bookingId)
                        .orElseThrow(() -> new IllegalArgumentException("Lịch hẹn không tồn tại."));
                if (!"CONFIRMED".equals(booking.status())) {
                    throw new IllegalStateException("Chỉ có thể tải ảnh cho lịch đang được xác nhận.");
                }
                if (!booking.staffId().equals(staffId)) {
                    throw new IllegalArgumentException("Chỉ nhân viên được phân công mới có thể tải ảnh minh chứng.");
                }
                Long imageId = bookingImages.insertImage(bookingId, finalForCleanup.getFileName().toString(),
                        LocalDateTime.now(BUSINESS_OFFSET).withNano(0));
                try {
                    moveIntoFinalStorage(tempForCleanup, finalForCleanup);
                } catch (IOException failure) {
                    throw new IllegalStateException("Could not finalize the evidence file", failure);
                }
                return imageId;
            });
            return saved;
        } catch (RuntimeException | Error failure) {
            deleteQuietly(finalFile);
            throw failure;
        } catch (IOException failure) {
            deleteQuietly(finalFile);
            throw new IllegalStateException("Could not store the evidence file", failure);
        } finally {
            deleteQuietly(temporaryFile);
        }
    }

    private ValidatedImage validate(MultipartFile upload) {
        if (upload == null || upload.isEmpty() || upload.getSize() > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("Evidence must be a non-empty image no larger than 5 MB");
        }
        String filename = upload.getOriginalFilename();
        if (filename == null) {
            throw new IllegalArgumentException("Evidence filename is required");
        }
        String lowerName = filename.toLowerCase(Locale.ROOT);
        String extension;
        if (lowerName.endsWith(".jpeg")) {
            extension = ".jpeg";
        } else if (lowerName.endsWith(".jpg")) {
            extension = ".jpg";
        } else if (lowerName.endsWith(".png")) {
            extension = ".png";
        } else {
            throw new IllegalArgumentException("Only JPEG and PNG evidence files are accepted");
        }

        try {
            byte[] bytes = upload.getBytes();
            String format = decodeFormat(bytes);
            if ((extension.equals(".png") && !format.equals("png"))
                    || (!extension.equals(".png") && !format.equals("jpeg"))) {
                throw new IllegalArgumentException("File extension does not match the image content");
            }
            return new ValidatedImage(bytes, extension);
        } catch (IOException failure) {
            throw new IllegalArgumentException("Evidence image could not be decoded", failure);
        }
    }

    private String decodeFormat(byte[] bytes) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) {
                throw new IllegalArgumentException("Evidence is not a decodable image");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException("Evidence is not a decodable image");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!format.equals("jpeg") && !format.equals("png")) {
                    throw new IllegalArgumentException("Only JPEG and PNG evidence content is accepted");
                }
                if (reader.read(0) == null) {
                    throw new IllegalArgumentException("Evidence image could not be decoded");
                }
                return format;
            } finally {
                reader.dispose();
            }
        }
    }

    private void moveIntoFinalStorage(Path temporaryFile, Path finalFile) throws IOException {
        try {
            Files.move(temporaryFile, finalFile, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(temporaryFile, finalFile);
        }
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Reconciliation removes stale temporary or unreferenced files.
        }
    }

    private record ValidatedImage(byte[] bytes, String extension) {
    }
}
