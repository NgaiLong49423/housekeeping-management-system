package com.housekeeping.management.dto.entity;

import com.housekeeping.management.enums.ServiceStatus;
import static org.assertj.core.api.Assertions.assertThat;

import com.housekeeping.management.enums.BookingStatus;
import com.housekeeping.management.enums.FeedbackStatus;
import com.housekeeping.management.enums.HouseAddressType;
import com.housekeeping.management.enums.LeaveStatus;
import com.housekeeping.management.enums.PaymentMethod;
import com.housekeeping.management.enums.PaymentStatus;
import com.housekeeping.management.enums.StaffType;
import com.housekeeping.management.enums.UserRole;
import com.housekeeping.management.enums.UserStatus;
import com.housekeeping.management.entity.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class EntityDtoConversionTest {
    private static final LocalDateTime DATE_TIME = LocalDateTime.of(2026, 11, 10, 9, 15);

    @Test
    void bookingConvertsBothWays() {
        BookingDTO dto = new BookingDTO(1L, 2L, 3L, 4L, 5L, "address", bd("10.1"), bd("106.2"), "note",
                DATE_TIME, DATE_TIME.plusHours(2), 120, bd("150000"), "area", BookingStatus.CONFIRMED,
                null, null, null, DATE_TIME, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                bd("0"), bd("0"), DATE_TIME, null, null, null, null);
        assertThat(BookingDTO.convertEntityToDTO(dto.convertDTOToEntity())).isEqualTo(dto);
    }

    @Test
    void bookingDetailConvertsBothWays() {
        BookingDetailDTO dto = new BookingDetailDTO(1L, 2L, 3L, "service", bd("100"), 60);
        assertThat(BookingDetailDTO.convertEntityToDTO(dto.convertDTOToEntity())).isEqualTo(dto);
    }

    @Test
    void bookingImageConvertsBothWays() {
        BookingImageDTO dto = new BookingImageDTO(1L, 2L, "image.png", DATE_TIME);
        assertThat(BookingImageDTO.convertEntityToDTO(dto.convertDTOToEntity())).isEqualTo(dto);
    }

    @Test
    void companyOperatingHourConvertsBothWays() {
        CompanyOperatingHourDTO dto = new CompanyOperatingHourDTO(1L, 2L, LocalDate.of(2026, 11, 1), 1,
                (short) 2, LocalTime.of(8, 0), LocalTime.of(17, 0), false);
        assertThat(CompanyOperatingHourDTO.convertEntityToDTO(dto.convertDTOToEntity())).isEqualTo(dto);
    }

    @Test
    void companySettingsConvertsBothWays() {
        CompanySettingsDTO dto = new CompanySettingsDTO(1L, "address", bd("10.1"), bd("106.2"), bd("15.00"));
        assertThat(CompanySettingsDTO.convertEntityToDTO(dto.convertDTOToEntity())).isEqualTo(dto);
    }

    @Test
    void feedbackConvertsBothWays() {
        FeedbackDTO dto = new FeedbackDTO(1L, 2L, 3L, "content", 5, "QUALITY", "reply", FeedbackStatus.OPEN);
        assertThat(FeedbackDTO.convertEntityToDTO(dto.convertDTOToEntity())).isEqualTo(dto);
    }

    @Test
    void houseConvertsBothWaysAndCanUpdateManagedEntity() {
        HouseDTO dto = new HouseDTO(1L, 2L, "Home", "address", bd("10.1"), bd("106.2"), "recipient", "0900",
                "city", "ward", "detail", HouseAddressType.HOME, true, "note", false);
        House entity = dto.convertDTOToEntity();
        assertThat(HouseDTO.convertEntityToDTO(entity)).isEqualTo(dto);
        House existing = new House();
        assertThat(dto.convertDTOToEntity(existing)).isSameAs(existing);
        assertThat(HouseDTO.convertEntityToDTO(existing)).isEqualTo(dto);
    }

    @Test
    void paymentConvertsBothWays() {
        PaymentDTO dto = new PaymentDTO(1L, 2L, bd("1000"), PaymentMethod.CASH, PaymentStatus.SUCCESS,
                "transaction", DATE_TIME, DATE_TIME.plusMinutes(1), false);
        assertThat(PaymentDTO.convertEntityToDTO(dto.convertDTOToEntity())).isEqualTo(dto);
    }

    @Test
    void servicesConvertsBothWaysAndCanUpdateManagedEntity() {
        ServicesDTO dto = new ServicesDTO(1L, "Cleaning", "description", "image", 60, bd("100000"), ServiceStatus.ACTIVE);
        Services entity = dto.convertDTOToEntity();
        assertThat(ServicesDTO.convertEntityToDTO(entity)).isEqualTo(dto);
        Services existing = new Services();
        assertThat(dto.convertDTOToEntity(existing)).isSameAs(existing);
        assertThat(ServicesDTO.convertEntityToDTO(existing)).isEqualTo(dto);
    }

    @Test
    void staffMapsNestedUserToDtoButLeavesEntityResolutionToService() {
        User user = new User();
        user.setUserId(7L);
        user.setFullName("Staff");
        user.setEmail("staff@example.test");
        user.setPhone("0900");
        user.setRole(UserRole.STAFF);
        user.setStatus(UserStatus.ACTIVE);
        user.setPasswordHash("secret-hash");
        Staff staff = new Staff();
        staff.setStaffId(8L);
        staff.setUser(user);
        staff.setStaffType(StaffType.FULL_TIME);

        StaffDTO dto = StaffDTO.convertEntityToDTO(staff);
        Staff converted = dto.convertDTOToEntity();

        assertThat(dto.user()).isEqualTo(new UserDTO(7L, "Staff", "staff@example.test", "0900", UserRole.STAFF, UserStatus.ACTIVE));
        assertThat(converted.getStaffId()).isEqualTo(8L);
        assertThat(converted.getStaffType()).isEqualTo(StaffType.FULL_TIME);
        assertThat(converted.getUser()).isNull();
        assertThat(java.util.Arrays.stream(UserDTO.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).toList()).doesNotContain("passwordHash");
    }

    @Test
    void staffAvailabilityConvertsBothWays() {
        StaffAvailabilityDTO dto = new StaffAvailabilityDTO(1L, 2L, LocalDate.of(2026, 11, 10),
                LocalTime.of(8, 0), LocalTime.of(12, 0), DATE_TIME, true);
        assertThat(StaffAvailabilityDTO.convertEntityToDTO(dto.convertDTOToEntity())).isEqualTo(dto);
    }

    @Test
    void staffFollowAndEmbeddedIdConvertBothWays() {
        StaffFollowIdDTO id = new StaffFollowIdDTO(2L, 3L);
        StaffFollowDTO dto = new StaffFollowDTO(id, DATE_TIME);
        assertThat(StaffFollowIdDTO.convertEntityToDTO(id.convertDTOToEntity())).isEqualTo(id);
        assertThat(StaffFollowDTO.convertEntityToDTO(dto.convertDTOToEntity())).isEqualTo(dto);
    }

    @Test
    void staffLeaveMapsNestedStaffToDtoButLeavesEntityResolutionToService() {
        Staff staff = new Staff();
        staff.setStaffId(8L);
        staff.setStaffType(StaffType.FULL_TIME);
        StaffLeave request = new StaffLeave();
        request.setLeaveId(9L);
        request.setStaff(staff);
        request.setStartDate(LocalDate.of(2026, 11, 11));
        request.setEndDate(LocalDate.of(2026, 11, 12));
        request.setReason("reason");
        request.setStatus(LeaveStatus.PENDING);
        request.setCreatedAt(DATE_TIME);

        StaffLeaveDTO dto = StaffLeaveDTO.convertEntityToDTO(request);
        StaffLeave converted = dto.convertDTOToEntity();

        assertThat(dto.staff()).isEqualTo(new StaffDTO(8L, null, StaffType.FULL_TIME));
        assertThat(converted.getLeaveId()).isEqualTo(9L);
        assertThat(converted.getStatus()).isEqualTo(LeaveStatus.PENDING);
        assertThat(converted.getStaff()).isNull();
    }

    @Test
    void staffProfileConvertsBothWays() {
        StaffProfileDTO dto = new StaffProfileDTO(1L, 2L, "avatar", "bio");
        assertThat(StaffProfileDTO.convertEntityToDTO(dto.convertDTOToEntity())).isEqualTo(dto);
    }

    @Test
    void userConversionRequiresHashSeparatelyAndNeverReturnsIt() {
        UserDTO dto = new UserDTO(1L, "Customer", "customer@example.test", "0900",
                UserRole.CUSTOMER, UserStatus.ACTIVE, true);
        User entity = dto.convertDTOToEntity("encoded-hash");

        assertThat(UserDTO.convertEntityToDTO(entity)).isEqualTo(dto);
        assertThat(entity.getPasswordHash()).isEqualTo("encoded-hash");
        assertThat(entity.isMustChangePassword()).isTrue();
        assertThat(java.util.Arrays.stream(UserDTO.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).toList()).doesNotContain("passwordHash");
    }

    @Test
    void entityToDtoConvertersReturnNullForNullEntity() {
        assertThat(BookingDTO.convertEntityToDTO(null)).isNull();
        assertThat(BookingDetailDTO.convertEntityToDTO(null)).isNull();
        assertThat(BookingImageDTO.convertEntityToDTO(null)).isNull();
        assertThat(CompanyOperatingHourDTO.convertEntityToDTO(null)).isNull();
        assertThat(CompanySettingsDTO.convertEntityToDTO(null)).isNull();
        assertThat(FeedbackDTO.convertEntityToDTO(null)).isNull();
        assertThat(HouseDTO.convertEntityToDTO(null)).isNull();
        assertThat(PaymentDTO.convertEntityToDTO(null)).isNull();
        assertThat(ServicesDTO.convertEntityToDTO(null)).isNull();
        assertThat(StaffDTO.convertEntityToDTO(null)).isNull();
        assertThat(StaffAvailabilityDTO.convertEntityToDTO(null)).isNull();
        assertThat(StaffFollowDTO.convertEntityToDTO(null)).isNull();
        assertThat(StaffFollowIdDTO.convertEntityToDTO(null)).isNull();
        assertThat(StaffLeaveDTO.convertEntityToDTO(null)).isNull();
        assertThat(StaffProfileDTO.convertEntityToDTO(null)).isNull();
        assertThat(UserDTO.convertEntityToDTO(null)).isNull();
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
