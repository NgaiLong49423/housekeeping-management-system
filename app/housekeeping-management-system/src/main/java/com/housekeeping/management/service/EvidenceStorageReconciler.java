package com.housekeeping.management.service;

import com.housekeeping.management.repository.BookingImageRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class EvidenceStorageReconciler implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(EvidenceStorageReconciler.class);
    private static final Duration STALE_FILE_AGE = Duration.ofHours(24);
    private final BookingImageRepository bookingImages;
    private final Path storageRoot;
    private final boolean cleanupEnabled;

    public EvidenceStorageReconciler(
            BookingImageRepository bookingImages,
            @Value("${app.evidence.storage-root:./var/evidence}") String storageRoot,
            @Value("${app.evidence.cleanup-enabled:false}") boolean cleanupEnabled) {
        this.bookingImages = bookingImages;
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
        this.cleanupEnabled = cleanupEnabled;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        Files.createDirectories(storageRoot);
        Set<String> referencedFiles = new HashSet<>();
        referencedFiles.addAll(bookingImages.findAllImageUrls());
        Instant staleBefore = Instant.now().minus(STALE_FILE_AGE);

        try (var files = Files.list(storageRoot)) {
            for (Path file : files.toList()) {
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                String filename = file.getFileName().toString();
                Instant modifiedAt = Files.getLastModifiedTime(file).toInstant();
                if (cleanupEnabled && !referencedFiles.contains(filename) && modifiedAt.isBefore(staleBefore)) {
                    Files.deleteIfExists(file);
                    LOGGER.info("Removed stale unreferenced evidence file {}", filename);
                }
            }
        }

        Path temporaryDirectory = storageRoot.resolve(".tmp");
        if (Files.isDirectory(temporaryDirectory)) {
            try (var temporaryFiles = Files.list(temporaryDirectory)) {
                for (Path file : temporaryFiles.toList()) {
                    if (cleanupEnabled && Files.isRegularFile(file)
                            && Files.getLastModifiedTime(file).toInstant().isBefore(staleBefore)) {
                        Files.deleteIfExists(file);
                    }
                }
            }
        }

        for (String filename : referencedFiles) {
            Path referenced = storageRoot.resolve(filename).normalize();
            if (!referenced.getParent().equals(storageRoot) || !Files.isRegularFile(referenced)) {
                LOGGER.error("Evidence metadata references a missing or invalid storage path {}", filename);
            }
        }
    }
}
