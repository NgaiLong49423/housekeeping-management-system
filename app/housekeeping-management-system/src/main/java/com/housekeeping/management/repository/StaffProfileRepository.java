package com.housekeeping.management.repository;

import com.housekeeping.management.dto.request.PortalForms.StaffProfileForm;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StaffProfileRepository {
    private final JdbcTemplate jdbc;
    public StaffProfileRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<StaffProfileForm> findByStaffId(long staffId) {
        List<StaffProfileForm> profiles = jdbc.query("""
                SELECT avatar_url, bio FROM dbo.Staff_Profiles WHERE staff_id=?
                """, (rs, row) -> new StaffProfileForm(rs.getString("avatar_url"), rs.getString("bio")), staffId);
        return profiles.stream().findFirst();
    }

    public void save(long staffId, String avatarUrl, String bio) {
        int updated = jdbc.update("""
                UPDATE dbo.Staff_Profiles SET avatar_url=?, bio=? WHERE staff_id=?
                """, avatarUrl, bio, staffId);
        if (updated == 0) jdbc.update("""
                INSERT INTO dbo.Staff_Profiles (staff_id, avatar_url, bio) VALUES (?, ?, ?)
                """, staffId, avatarUrl, bio);
    }
}
