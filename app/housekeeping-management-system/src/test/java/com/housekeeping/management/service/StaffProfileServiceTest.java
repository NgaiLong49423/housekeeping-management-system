package com.housekeeping.management.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.housekeeping.management.enums.UserStatus;
import com.housekeeping.management.enums.UserRole;
import com.housekeeping.management.dto.request.PortalForms.StaffProfileForm;
import com.housekeeping.management.entity.Staff;
import com.housekeeping.management.entity.StaffProfile;
import com.housekeeping.management.entity.User;
import com.housekeeping.management.repository.StaffProfileRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class StaffProfileServiceTest {
    private final StaffProfileRepository repository = mock(StaffProfileRepository.class);
    private final StaffManagementService staffManagement = mock(StaffManagementService.class);
    private final StaffProfileService service = new com.housekeeping.management.service.impl.StaffProfileServiceImpl(repository, staffManagement);

    @Test
    void returnsAnEmptyProfileWhenStaffHasNotCreatedOne() {
        when(staffManagement.findActiveByUserId(7L)).thenReturn(staff(12L));
        when(repository.findByStaffId(12L)).thenReturn(Optional.empty());

        assertThat(service.findOwnProfile(7L)).isEqualTo(new StaffProfileForm(null, ""));
    }

    @Test
    void trimsProfileAndSavesHttpsAvatarForAuthenticatedStaffId() {
        when(staffManagement.findActiveByUserIdForUpdate(7L)).thenReturn(staff(12L));

        service.updateOwnProfile(7L, new StaffProfileForm(" https://example.test/avatar.png ", "  Giới thiệu  "));

        var captured = org.mockito.ArgumentCaptor.forClass(StaffProfile.class);
        verify(repository).save(captured.capture());
        assertThat(captured.getValue().getStaffId()).isEqualTo(12L);
        assertThat(captured.getValue().getAvatarUrl()).isEqualTo("https://example.test/avatar.png");
        assertThat(captured.getValue().getBio()).isEqualTo("Giới thiệu");
    }

    @Test
    void rejectsNonHttpsAvatar() {
        when(staffManagement.findActiveByUserIdForUpdate(7L)).thenReturn(staff(12L));

        assertThatThrownBy(() -> service.updateOwnProfile(7L, new StaffProfileForm("javascript:alert(1)", "bio")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("HTTPS");
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any(StaffProfile.class));
    }

    @Test
    void rejectsUnknownStaffWithoutSavingProfile() {
        when(staffManagement.findActiveByUserIdForUpdate(7L)).thenThrow(new AccessDeniedException("inactive"));

        assertThatThrownBy(() -> service.updateOwnProfile(7L, new StaffProfileForm(null, "bio")))
                .isInstanceOf(AccessDeniedException.class);
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any(StaffProfile.class));
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
