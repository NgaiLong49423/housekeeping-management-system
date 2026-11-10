package com.housekeeping.management.repository;

import com.housekeeping.management.entity.User;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from User user where user.email = :email")
    Optional<User> findByEmailForUpdate(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from User user where user.userId=:userId and user.role=com.housekeeping.management.domain.UserRole.CUSTOMER and user.status=com.housekeeping.management.domain.UserStatus.ACTIVE")
    Optional<User> findActiveCustomerByIdForUpdate(@Param("userId") Long userId);
}
