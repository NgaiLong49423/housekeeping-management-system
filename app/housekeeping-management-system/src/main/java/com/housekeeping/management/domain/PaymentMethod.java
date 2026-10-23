package com.housekeeping.management.domain;

public enum PaymentMethod {
    CASH,
    BANK_TRANSFER,
    PAYOS,
    /** Retained only so existing MoMo payment rows can still be read. */
    MOMO
}
