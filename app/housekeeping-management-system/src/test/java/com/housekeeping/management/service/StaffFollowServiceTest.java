package com.housekeeping.management.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.housekeeping.management.entity.Staff;
import com.housekeeping.management.entity.User;
import com.housekeeping.management.entity.StaffFollow;
import com.housekeeping.management.entity.StaffFollowId;
import com.housekeeping.management.repository.StaffFollowRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class StaffFollowServiceTest {
    private final StaffManagementService staffManagement = mock(StaffManagementService.class);
    private final AuthService auth = mock(AuthService.class);
    private final StaffFollowRepository follows = mock(StaffFollowRepository.class);
    private final StaffFollowService service = new com.housekeeping.management.service.impl.StaffFollowServiceImpl(
            staffManagement, auth, follows);

    @Test
    void followingResolvesBothAccountsThroughTheirDomainServices() {
        service.setFollowing(12L, 34L, true);

        verify(staffManagement).findActiveByStaffId(12L);
        verify(auth).findActiveCustomerForUpdate(34L);
        verify(follows).existsById(new StaffFollowId(34L, 12L));
        verify(follows).save(argThat(follow -> follow.getId().equals(new StaffFollowId(34L, 12L))));
    }

    @Test
    void invalidCustomerCannotCreateOrRemoveFollow() {
        when(auth.findActiveCustomerForUpdate(34L)).thenThrow(new AccessDeniedException("inactive"));

        assertThatThrownBy(() -> service.setFollowing(12L, 34L, false))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(follows);
    }
}
