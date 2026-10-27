package com.housekeeping.management.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.housekeeping.management.domain.UserStatus;
import com.housekeeping.management.domain.UserRole;
import com.housekeeping.management.dto.request.PortalForms.StaffProfileForm;
import com.housekeeping.management.entity.Staff;
import com.housekeeping.management.entity.User;
import com.housekeeping.management.repository.StaffAccountRepository;
import com.housekeeping.management.repository.StaffProfileRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class StaffProfileServiceTest {
    private final StaffProfileRepository repository = mock(StaffProfileRepository.class);
    private final StaffAccountRepository staffAccounts = mock(StaffAccountRepository.class);
    private final StaffProfileService service = new StaffProfileService(repository, staffAccounts);

    @Test
    void returnsAnEmptyProfileWhenStaffHasNotCreatedOne() {
        when(staffAccounts.findByUser_UserId(7L)).thenReturn(Optional.of(staff(12L)));
        when(repository.findByStaffId(12L)).thenReturn(Optional.empty());

        assertThat(service.findOwnProfile(7L)).isEqualTo(new StaffProfileForm(null, ""));
    }

    @Test
    void trimsProfileAndSavesHttpsAvatarForAuthenticatedStaffId() {
        when(staffAccounts.findActiveByUserIdForUpdate(7L)).thenReturn(Optional.of(staff(12L)));

        service.updateOwnProfile(7L, new StaffProfileForm(" https://example.test/avatar.png ", "  Giới thiệu  "));

        verify(repository).save(12L, "https://example.test/avatar.png", "Giới thiệu");
    }

    @Test
    void rejectsNonHttpsAvatar() {
        when(staffAccounts.findActiveByUserIdForUpdate(7L)).thenReturn(Optional.of(staff(12L)));

        assertThatThrownBy(() -> service.updateOwnProfile(7L, new StaffProfileForm("javascript:alert(1)", "bio")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("HTTPS");
        verify(repository, never()).save(12L, "javascript:alert(1)", "bio");
    }

    @Test
    void rejectsUnknownStaffWithoutSavingProfile() {
        when(staffAccounts.findActiveByUserIdForUpdate(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateOwnProfile(7L, new StaffProfileForm(null, "bio")))
                .isInstanceOf(AccessDeniedException.class);
        verify(repository, never()).save(12L, null, "bio");
    }

    private Staff staff(Long staffId) {
        User user = new User();
        user.setRole(UserRole.STAFF);
        user.setStatus(UserStatus.ACTIVE);
        Staff staff = new Staff();
        staff.setStaffId(staffId);
        staff.setUser(user);
        return staff;
    }
}
