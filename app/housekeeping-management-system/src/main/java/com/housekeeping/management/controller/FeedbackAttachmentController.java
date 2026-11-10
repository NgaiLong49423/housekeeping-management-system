package com.housekeeping.management.controller;

import com.housekeeping.management.service.CaseAttachmentStorageService;
import com.housekeeping.management.web.PortalUser;
import java.nio.file.Path;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class FeedbackAttachmentController {
    private final CaseAttachmentStorageService storage;
    public FeedbackAttachmentController(CaseAttachmentStorageService storage) { this.storage=storage; }

    @GetMapping("/feedback-case-evidence/{id}")
    ResponseEntity<Resource> evidence(@PathVariable Long id, @AuthenticationPrincipal PortalUser user) {
        if(user==null || !(user.getRole().equals("CUSTOMER") || user.isAdmin()))
            throw new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
        Path file=storage.customerEvidence(id,user.getUserId(),user.isAdmin());
        return response(file);
    }

    @GetMapping("/admin/financial-entries/{id}/receipt")
    ResponseEntity<Resource> receipt(@PathVariable Long id, @AuthenticationPrincipal PortalUser user) {
        Path file=storage.receipt(id,user.getUserId());
        return response(file);
    }

    private ResponseEntity<Resource> response(Path file) {
        String lower=file.toString().toLowerCase(java.util.Locale.ROOT);
        MediaType mime=lower.endsWith(".pdf")?MediaType.APPLICATION_PDF:lower.endsWith(".png")?MediaType.IMAGE_PNG:MediaType.IMAGE_JPEG;
        return ResponseEntity.ok().contentType(mime).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,"inline").body(new FileSystemResource(file));
    }
}
