package com.housekeeping.management.service;

import com.housekeeping.management.dto.response.PortalViews.CustomerAccountCard;
import java.util.List;

/** Admin và Manager xem, khóa (bắt buộc có lý do) và mở khóa tài khoản Customer. */
public interface CustomerManagementService {
    List<CustomerAccountCard> findCustomers(Long adminId, String keyword);

    void lock(Long adminId, Long customerId, String reason);

    void unlock(Long adminId, Long customerId);
}
