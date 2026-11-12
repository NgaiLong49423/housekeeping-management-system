package com.housekeeping.management.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SpecificAddressRulesTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "12 Nguyễn Trãi",
            "12/3 hẻm 45 Nguyễn Trãi",
            "Hẻm 34/5 Lê Lợi",
            "Căn hộ 12.05, 45 Nguyễn Thị Minh Khai",
            "Số 9 ngõ 21 Hoàng Hoa Thám",
            "72 Võ Văn Tần, Phường Xuân Hòa"
    })
    void acceptsAddressesWithHouseNumberAndStreet(String address) {
        assertThat(SpecificAddressRules.isSpecific(address)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "   ",
            "Nguyễn Trãi",
            "Phường Bến Thành",
            "Quận 1",
            "Q.1, TP.HCM",
            "Phường 5, Quận 3",
            "123",
            "Địa chỉ mẫu, Quận 1"
    })
    void rejectsGenericOrIncompleteAddresses(String address) {
        assertThat(SpecificAddressRules.isSpecific(address)).isFalse();
        assertThatThrownBy(() -> SpecificAddressRules.requireSpecific(address))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("số nhà");
    }

    @org.junit.jupiter.api.Test
    void normalizesWhitespaceAndRejectsOverlongInput() {
        assertThat(SpecificAddressRules.requireSpecific("  12   Nguyễn\tTrãi ")).isEqualTo("12 Nguyễn Trãi");
        assertThat(SpecificAddressRules.isSpecific("12 " + "a".repeat(SpecificAddressRules.MAX_LENGTH))).isFalse();
    }
}
