package com.housekeeping.management.repository;

import com.housekeeping.management.enums.UserRole;
import com.housekeeping.management.entity.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAccountRepository extends JpaRepository<User, Long> {
    List<User> findByRoleOrderByStatusAscFullNameAsc(UserRole role);

    Optional<User> findByUserIdAndRole(Long userId, UserRole role);
}
