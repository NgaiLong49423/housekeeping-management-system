package com.housekeeping.management.startup;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/** Copies illustrative feedback photos into the protected evidence store in the demo profile. */
@Component
@Profile("demo")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DemoFeedbackEvidenceInstaller implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(DemoFeedbackEvidenceInstaller.class);
    private static final List<DemoEvidence> EVIDENCE = List.of(
            new DemoEvidence("classpath:demo-evidence/feedback-cleanliness.png", "demo-feedback-cleanliness.png"),
            new DemoEvidence("classpath:demo-evidence/feedback-damaged-vase.png", "demo-feedback-damaged-vase.png"));

    private final ResourceLoader resourceLoader;
    private final Path storageRoot;

    public DemoFeedbackEvidenceInstaller(
            ResourceLoader resourceLoader,
            @Value("${app.evidence.storage-root:./var/evidence}") String storageRoot) {
        this.resourceLoader = resourceLoader;
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        Files.createDirectories(storageRoot);
        for (DemoEvidence evidence : EVIDENCE) {
            installIfMissing(evidence);
        }
    }

    private void installIfMissing(DemoEvidence evidence) throws IOException {
        Path target = storageRoot.resolve(evidence.filename()).normalize();
        if (!target.getParent().equals(storageRoot)) {
            throw new IllegalStateException("Demo evidence path must remain inside the evidence storage root.");
        }
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }

        Resource resource = resourceLoader.getResource(evidence.classpathLocation());
        if (!resource.exists()) {
            throw new IllegalStateException("Required demo feedback evidence is missing: " + evidence.classpathLocation());
        }
        try (InputStream input = resource.getInputStream()) {
            try {
                Files.copy(input, target);
                LOGGER.info("Installed illustrative demo feedback evidence {}", evidence.filename());
            } catch (java.nio.file.FileAlreadyExistsException ignored) {
                // Another startup process installed the same immutable demo asset first.
            }
        }
    }

    private record DemoEvidence(String classpathLocation, String filename) { }
}
