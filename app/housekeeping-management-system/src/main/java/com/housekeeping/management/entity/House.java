package com.housekeeping.management.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import com.housekeeping.management.dto.SpecificAddressRules;
import com.housekeeping.management.enums.HouseAddressType;
import java.math.BigDecimal;
import java.util.Locale;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

@Entity
@Table(name = "Houses", schema = "dbo")
@Getter
@Setter
@NoArgsConstructor
public class House {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "house_id", nullable = false)
    private Long houseId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "house_name", length = 100, nullable = false)
    private String houseName;

    @Column(name = "address", length = 500, nullable = false)
    private String address;

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    @Nationalized
    @Column(name = "recipient_name", length = 150, nullable = false)
    private String recipientName;

    @Nationalized
    @Column(name = "recipient_phone", length = 20, nullable = false)
    private String recipientPhone;

    @Nationalized
    @Column(name = "province_city", length = 100)
    private String provinceCity;

    @Nationalized
    @Column(name = "ward_commune", length = 100)
    private String wardCommune;

    @Nationalized
    @Column(name = "address_detail", length = 500, nullable = false)
    private String addressDetail;

    @Enumerated(EnumType.STRING)
    @Column(name = "address_type", length = 20, nullable = false, columnDefinition = "varchar(20)")
    private HouseAddressType addressType = HouseAddressType.HOME;

    @Column(name = "is_default", nullable = false)
    private boolean defaultAddress;

    @Column(name = "address_note", length = 500)
    private String addressNote;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    /** Phần số nhà/tên đường đã đủ cụ thể để đặt lịch hay chưa. */
    public boolean isSpecificAddress() {
        return SpecificAddressRules.isSpecific(addressDetail);
    }

    /**
     * Địa chỉ đầy đủ cho Staff: số nhà/tên đường khách tự nhập ({@code addressDetail}) ghép với địa chỉ VietMap
     * ({@code address}). Không lặp lại khi địa chỉ VietMap đã chứa sẵn phần chi tiết.
     */
    public String getFullAddress() {
        String detail = addressDetail == null ? "" : addressDetail.strip();
        String located = address == null ? "" : address.strip();
        if (detail.isEmpty()) return located;
        if (located.isEmpty()) return detail;
        if (located.toLowerCase(Locale.ROOT).contains(detail.toLowerCase(Locale.ROOT))) return located;
        return detail + ", " + located;
    }
}
