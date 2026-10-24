package com.housekeeping.management.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.housekeeping.management.SqlServerIntegrationTest;
import com.housekeeping.management.domain.StaffType;
import com.housekeeping.management.domain.UserRole;
import com.housekeeping.management.dto.request.PortalForms.StaffProfileForm;
import com.housekeeping.management.entity.Staff;
import com.housekeeping.management.entity.User;
import com.housekeeping.management.repository.StaffAccountRepository;
import com.housekeeping.management.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class StaffProfileSqlServerTest extends SqlServerIntegrationTest {
    @Autowired private StaffProfileService profiles;
    @Autowired private StaffAccountRepository staffAccounts;
    @Autowired private UserRepository users;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void staffCanCreateAndUpdateOwnProfileRow() {
        User user = new User();
        String suffix = UUID.randomUUID().toString();
        user.setFullName("Profile test " + suffix);
        user.setEmail("profile-" + suffix + "@example.test");
        user.setPasswordHash("{bcrypt}unused-test-hash");
        user.setPhone("0900000000");
        user.setRole(UserRole.STAFF);
        user.setStatus(com.housekeeping.management.domain.UserStatus.ACTIVE);
        User savedUser = users.saveAndFlush(user);
        Staff staff = new Staff();
        staff.setUser(savedUser);
        staff.setStaffType(StaffType.FULL_TIME);
        long staffId = staffAccounts.saveAndFlush(staff).getStaffId();

        profiles.updateOwnProfile(savedUser.getUserId(), new StaffProfileForm("https://example.test/avatar.png", "Giới thiệu ban đầu"));
        profiles.updateOwnProfile(savedUser.getUserId(), new StaffProfileForm(null, "Giới thiệu đã sửa"));

        assertThat(profiles.findOwnProfile(savedUser.getUserId())).isEqualTo(new StaffProfileForm(null, "Giới thiệu đã sửa"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Staff_Profiles WHERE staff_id=?", Integer.class, staffId)).isEqualTo(1);
    }
}
