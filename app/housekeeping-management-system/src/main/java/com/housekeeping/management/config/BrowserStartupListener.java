package com.housekeeping.management.config;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class BrowserStartupListener implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(BrowserStartupListener.class);

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!(event.getApplicationContext() instanceof ServletWebServerApplicationContext context)
                || GraphicsEnvironment.isHeadless() || !Desktop.isDesktopSupported()) {
            return;
        }

        Environment environment = context.getEnvironment();
        String port = environment.getProperty("local.server.port",
                environment.getProperty("server.port", "8080"));
        String address = environment.getProperty("server.address", "localhost");
        if (address == null || address.isBlank() || "0.0.0.0".equals(address) || "::".equals(address)) {
            address = "localhost";
        }

        try {
            Desktop.getDesktop().browse(URI.create("http://" + address + ":" + port));
        } catch (IOException | IllegalArgumentException | UnsupportedOperationException exception) {
            log.warn("Application started, but the browser could not be opened automatically.", exception);
        }
    }
}
