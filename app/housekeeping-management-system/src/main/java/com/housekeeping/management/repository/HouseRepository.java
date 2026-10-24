package com.housekeeping.management.repository;

import com.housekeeping.management.entity.House;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface HouseRepository extends JpaRepository<House, Long> {
    List<House> findAllByCustomerIdAndDeletedFalseOrderByHouseName(Long customerId);
    Optional<House> findByHouseIdAndCustomerIdAndDeletedFalse(Long houseId, Long customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<House> findLockedByHouseIdAndCustomerIdAndDeletedFalse(Long houseId, Long customerId);
}
