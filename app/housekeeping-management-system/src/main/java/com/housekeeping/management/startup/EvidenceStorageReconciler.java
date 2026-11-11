package com.housekeeping.management.startup;

import com.housekeeping.management.repository.BookingImageRepository;
import com.housekeeping.management.repository.BookingFinancialRepository;
import com.housekeeping.management.repository.FeedbackCaseEvidenceRepository;
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
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class EvidenceStorageReconciler implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(EvidenceStorageReconciler.class);
    private static final Duration STALE_FILE_AGE = Duration.ofHours(24);
    private final BookingImageRepository bookingImages;
    private final BookingFinancialRepository financialEntries;
    private final FeedbackCaseEvidenceRepository caseEvidence;
    private final Path storageRoot;
    private final boolean cleanupEnabled;

    public EvidenceStorageReconciler(
            BookingImageRepository bookingImages,
            BookingFinancialRepository financialEntries,
            FeedbackCaseEvidenceRepository caseEvidence,
            @Value("${app.evidence.storage-root:./var/evidence}") String storageRoot,
            @Value("${app.evidence.cleanup-enabled:false}") boolean cleanupEnabled) {
        this.bookingImages = bookingImages;
        this.financialEntries = financialEntries;
        this.caseEvidence = caseEvidence;
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
        this.cleanupEnabled = cleanupEnabled;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        Files.createDirectories(storageRoot);
        Set<String> referencedFiles = new HashSet<>();
        bookingImages.findAll().stream().map(com.housekeeping.management.entity.BookingImage::getImageUrl)
                .forEach(referencedFiles::add);
        caseEvidence.findAll().stream().map(com.housekeeping.management.entity.FeedbackCaseEvidence::getImagePath)
                .forEach(referencedFiles::add);
        financialEntries.findAll().stream().map(com.housekeeping.management.entity.BookingFinancialEntry::getReceiptPath)
                .filter(java.util.Objects::nonNull).forEach(referencedFiles::add);
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
