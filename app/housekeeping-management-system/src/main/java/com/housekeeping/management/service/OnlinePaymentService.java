package com.housekeeping.management.service;

import com.housekeeping.management.enums.ReturnStatus;
import java.util.Map;

public interface OnlinePaymentService {
    record Checkout(String redirectUrl, boolean alreadyPaid) { }

    record ReturnOutcome(Long bookingId, ReturnStatus status) { }

    record IpnResponse(String rspCode, String message) { }

    public Checkout checkout(Long bookingId, Long customerId, String clientIp);

    public ReturnOutcome handleReturn(Map<String, String> params);

    IpnResponse handleIpn(Map<String, String> params);
}
