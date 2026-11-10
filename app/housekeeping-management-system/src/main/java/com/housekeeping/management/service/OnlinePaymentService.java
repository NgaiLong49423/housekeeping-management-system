package com.housekeeping.management.service;

import java.util.Map;

public interface OnlinePaymentService {
    record Checkout(String redirectUrl, boolean alreadyPaid) { }

    enum ReturnStatus { PAID, FAILED, PENDING }

    record ReturnOutcome(Long bookingId, ReturnStatus status) { }

    record IpnResponse(String rspCode, String message) { }

    public Checkout checkout(Long bookingId, Long customerId, String clientIp);

    public ReturnOutcome handleReturn(Map<String, String> params);

    IpnResponse handleIpn(Map<String, String> params);
}
