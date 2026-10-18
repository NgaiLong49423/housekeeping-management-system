package com.housekeeping.management.web;

import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import com.housekeeping.management.service.GlobalEmailUniquenessService;

@Service
public class PortalIdentityService implements UserDetailsService {
    private final JdbcTemplate jdbc;

    public PortalIdentityService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public UserDetails loadUserByUsername(String email) {
        String canonical = GlobalEmailUniquenessService.canonicalize(email);
        List<PortalUser> matches = new ArrayList<>();
        find(matches, "SELECT customer_id, email, password_hash, display_name FROM Customer WHERE email = ?", canonical, "CUSTOMER");
        find(matches, "SELECT staff_id, email, password_hash, display_name FROM Staff WHERE email = ?", canonical, "STAFF");
        find(matches, "SELECT admin_id, email, password_hash, display_name FROM Admin WHERE email = ?", canonical, "ADMIN");
        // Legacy cross-role collisions must not silently grant the first matching role.
        if (matches.size() != 1) { throw new UsernameNotFoundException("Thông tin đăng nhập không hợp lệ."); }
        return matches.getFirst();
    }

    private void find(List<PortalUser> matches, String sql, String email, String role) {
        matches.addAll(jdbc.query(sql, (rs, row) -> new PortalUser(rs.getLong(1), rs.getString(2),
                rs.getString(3), rs.getString(4), role), email));
    }
}
