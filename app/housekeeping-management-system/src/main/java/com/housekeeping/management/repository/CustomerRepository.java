package com.housekeeping.management.repository;


import org.springframework.data.repository.NoRepositoryBean;
import com.housekeeping.management.domain.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

@NoRepositoryBean
public interface CustomerRepository extends JpaRepository<Customer, Long> {
}
