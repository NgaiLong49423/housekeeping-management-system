package com.housekeeping.management.repository;

import com.housekeeping.management.entity.User;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Tài khoản có role CUSTOMER, dùng cho màn hình quản lý Customer của Admin/Manager. */
public interface CustomerAccountRepository extends JpaRepository<User, Long> {
    /** {@code keyword} đã viết thường và bọc dấu %, hoặc null để lấy tất cả. */
    @Query("select user from User user where user.role=com.housekeeping.management.enums.UserRole.CUSTOMER "
            + "and (:keyword is null or lower(user.fullName) like :keyword or lower(user.email) like :keyword "
            + "or user.phone like :keyword) order by user.status asc, user.fullName asc")
    List<User> search(@Param("keyword") String keyword);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from User user where user.userId=:userId "
            + "and user.role=com.housekeeping.management.enums.UserRole.CUSTOMER")
    Optional<User> findCustomerForUpdate(@Param("userId") Long userId);
}
