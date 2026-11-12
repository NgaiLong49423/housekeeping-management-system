package com.housekeeping.management.startup;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Opens the local application after the web server is ready when explicitly enabled by a run configuration. */
@Component
@ConditionalOnProperty(prefix = "app.browser", name = "open-on-startup", havingValue = "true")
public class DevelopmentBrowserLauncher {
    private static final Logger log = LoggerFactory.getLogger(DevelopmentBrowserLauncher.class);

    private final Environment environment;

    public DevelopmentBrowserLauncher(Environment environment) {
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void openWhenApplicationIsReady(ApplicationReadyEvent event) {
        if (GraphicsEnvironment.isHeadless() || !Desktop.isDesktopSupported()) {
            log.info("Skipping automatic browser launch because this environment has no desktop browser.");
            return;
        }

        Desktop desktop = Desktop.getDesktop();
        if (!desktop.isSupported(Desktop.Action.BROWSE)) {
            log.info("Skipping automatic browser launch because desktop browsing is unavailable.");
            return;
        }

        int configuredPort = environment.getProperty("server.port", Integer.class, 8080);
        int port = environment.getProperty("local.server.port", Integer.class, configuredPort);
        String contextPath = environment.getProperty("server.servlet.context-path", "");
        String normalizedContextPath = contextPath.isBlank() || "/".equals(contextPath)
                ? "" : "/" + contextPath.replaceAll("^/+|/+$", "");
        URI applicationUrl = URI.create("http://localhost:" + port + normalizedContextPath + "/");

        try {
            desktop.browse(applicationUrl);
            log.info("Opened the local application in the default browser: {}", applicationUrl);
        } catch (Exception failure) {
            log.warn("The application is ready, but the default browser could not be opened at {}", applicationUrl,
                    failure);
        }
    }
}
