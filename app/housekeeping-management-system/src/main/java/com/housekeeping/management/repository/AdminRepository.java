package com.housekeeping.management.repository;


import org.springframework.data.repository.NoRepositoryBean;
import com.housekeeping.management.domain.entity.Admin;
import org.springframework.data.jpa.repository.JpaRepository;

@NoRepositoryBean
public interface AdminRepository extends JpaRepository<Admin, Long> {
}
