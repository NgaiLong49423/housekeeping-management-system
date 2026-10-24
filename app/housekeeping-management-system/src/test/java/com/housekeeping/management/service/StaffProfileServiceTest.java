package com.housekeeping.management.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.housekeeping.management.dto.request.PortalForms.StaffProfileForm;
import com.housekeeping.management.repository.StaffProfileRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StaffProfileServiceTest {
    private final StaffProfileRepository repository = mock(StaffProfileRepository.class);
    private final StaffProfileService service = new StaffProfileService(repository);

    @Test
    void returnsAnEmptyProfileWhenStaffHasNotCreatedOne() {
        when(repository.findByStaffId(12L)).thenReturn(Optional.empty());

        assertThat(service.findOwnProfile(12L)).isEqualTo(new StaffProfileForm(null, ""));
    }

    @Test
    void trimsProfileAndSavesHttpsAvatarForAuthenticatedStaffId() {
        when(repository.lockStaff(12L)).thenReturn(true);

        service.updateOwnProfile(12L, new StaffProfileForm(" https://example.test/avatar.png ", "  Giới thiệu  "));

        verify(repository).save(12L, "https://example.test/avatar.png", "Giới thiệu");
    }

    @Test
    void rejectsNonHttpsAvatar() {
        when(repository.lockStaff(12L)).thenReturn(true);

        assertThatThrownBy(() -> service.updateOwnProfile(12L, new StaffProfileForm("javascript:alert(1)", "bio")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("HTTPS");
        verify(repository, never()).save(12L, "javascript:alert(1)", "bio");
    }

    @Test
    void rejectsUnknownStaffWithoutSavingProfile() {
        when(repository.lockStaff(12L)).thenReturn(false);

        assertThatThrownBy(() -> service.updateOwnProfile(12L, new StaffProfileForm(null, "bio")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Không tìm thấy");
        verify(repository, never()).save(12L, null, "bio");
    }
}
