package com.housekeeping.management.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.housekeeping.management.dto.BookingDelayCompensation.Tier;
import com.housekeeping.management.repository.BookingDelayCompensationRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class BookingDelayCompensationSettingsServiceTest {
    private final BookingDelayCompensationRepository repository = mock(BookingDelayCompensationRepository.class);
    private final BookingDelayCompensationSettingsService service =
            new com.housekeeping.management.service.impl.BookingDelayCompensationSettingsServiceImpl(repository);

    @Test
    void acceptsThirtyPercentAsMaximumConfiguredDiscount() {
        service.save(List.of(new Tier(1, null, 30)));

        verify(repository).deleteAllInBatch();
        verify(repository).saveAll(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void rejectsDiscountAboveThirtyPercentBeforeChangingStoredTiers() {
        assertThatThrownBy(() -> service.save(List.of(new Tier(1, null, 31))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("0 đến 30");

        verify(repository, never()).deleteAllInBatch();
        verify(repository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
    }
}
