package com.housekeeping.management.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;

/** Đăng nhập sai chuyển về {@code /login?error}; tài khoản bị khóa (đúng mật khẩu) chuyển về {@code /login?locked}. */
public class LockedAccountLoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {
    /** Session attribute chứa lý do khóa, được trang đăng nhập đọc một lần rồi xóa. */
    public static final String LOCK_REASON_ATTRIBUTE = "LOGIN_LOCK_REASON";

    public LockedAccountLoginFailureHandler() {
        super("/login?error");
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {
        if (exception instanceof LockedException) {
            String reason = exception.getMessage();
            request.getSession().setAttribute(LOCK_REASON_ATTRIBUTE, reason == null ? "" : reason);
            getRedirectStrategy().sendRedirect(request, response, "/login?locked");
            return;
        }
        super.onAuthenticationFailure(request, response, exception);
    }
}
