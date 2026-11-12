package com.housekeeping.management.web;

import com.housekeeping.management.service.AccountAuthorizationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Tài khoản bị khóa trong lúc đang đăng nhập: ở request kế tiếp, phiên bị hủy và người dùng được đưa về
 * trang đăng nhập kèm lý do khóa, thay vì tiếp tục dùng phiên cũ.
 */
public class LockedAccountSessionFilter extends OncePerRequestFilter {
    private final AccountAuthorizationService authorization;

    public LockedAccountSessionFilter(AccountAuthorizationService authorization) {
        this.authorization = authorization;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/images/")
                || path.startsWith("/vendor/") || path.equals("/login") || path.equals("/error");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof PortalUser user) {
            Optional<String> lockReason = authorization.lockReasonIfInactive(user.getUserId());
            if (lockReason.isPresent()) {
                SecurityContextHolder.clearContext();
                HttpSession session = request.getSession(false);
                if (session != null) session.invalidate();
                request.getSession(true).setAttribute(LockedAccountLoginFailureHandler.LOCK_REASON_ATTRIBUTE, lockReason.get());
                response.sendRedirect(request.getContextPath() + "/login?locked");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
