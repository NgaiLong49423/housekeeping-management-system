package com.housekeeping.management.repository;

import com.housekeeping.management.entity.House;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface HouseRepository extends JpaRepository<House, Long> {
    List<House> findAllByCustomerIdAndDeletedFalseOrderByDefaultAddressDescHouseNameAsc(Long customerId);
    Optional<House> findByHouseIdAndCustomerIdAndDeletedFalse(Long houseId, Long customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<House> findLockedByHouseIdAndCustomerIdAndDeletedFalse(Long houseId, Long customerId);

    /** Số địa chỉ đang dùng của một Customer (Spring Data tự gán theo tên cột {@code as ...}). */
    interface CustomerHouseCountRow {
        Long getCustomerId();
        Long getHouseCount();
    }

    @Query("select house.customerId as customerId, count(house) as houseCount from House house "
            + "where house.deleted=false group by house.customerId")
    List<CustomerHouseCountRow> countActiveHousesByCustomer();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update House house set house.defaultAddress=false where house.customerId=:customerId "
            + "and house.deleted=false and house.defaultAddress=true and (:exceptId is null or house.houseId<>:exceptId)")
    int clearDefaultAddress(@Param("customerId") Long customerId, @Param("exceptId") Long exceptId);
}
