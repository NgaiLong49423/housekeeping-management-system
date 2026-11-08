package com.housekeeping.management.web;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import com.housekeeping.management.service.DuplicateEmailException;

@ControllerAdvice
public class PortalAdvice {
    public static String cleanLabel(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String cleaned = value
                .replaceAll("(?iu)\\s*\\(\\s*demo\\s*\\)", "")
                .replaceAll("(?iu)(?<=^|[\\s·\\-_])demo(?=$|[\\s·\\-_])", "")
                .replaceAll("\\s{2,}", " ")
                .trim();
        return cleaned.isEmpty() ? value.trim() : cleaned;
    }

    public enum UiText {
        INSTANCE;

        public String label(String value) {
            return cleanLabel(value);
        }

        public String initial(String value) {
            String cleaned = cleanLabel(value);
            return (cleaned == null || cleaned.isEmpty()) ? "?" : cleaned.substring(0, 1).toUpperCase();
        }
    }

    @ModelAttribute("ui")
    UiText ui() { return UiText.INSTANCE; }

    @ModelAttribute
    void identity(@AuthenticationPrincipal PortalUser user, Model model) { model.addAttribute("currentUser", user); }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    String business(RuntimeException failure, Model model) {
        model.addAttribute("ui", UiText.INSTANCE);
        model.addAttribute("error", PortalMessages.business(failure)); return "error";
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    String dependency(Model model) {
        model.addAttribute("ui", UiText.INSTANCE);
        model.addAttribute("error", "Không thể lưu thay đổi: dữ liệu bị trùng hoặc đang được sử dụng. Lịch sử được giữ nguyên."); return "error";
    }

    @ExceptionHandler(DuplicateEmailException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    String duplicateEmail(Model model) {
        model.addAttribute("ui", UiText.INSTANCE);
        model.addAttribute("error", "Email đã được sử dụng. Vui lòng chọn email khác."); return "error";
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    String upload(Model model) {
        model.addAttribute("ui", UiText.INSTANCE);
        model.addAttribute("error", "Ảnh vượt quá 5 MB. Hãy chọn file nhỏ hơn."); return "error";
    }
}
