package com.housekeeping.management.integration.vnpay;

import java.math.BigDecimal;

/** Các mã VNPay trả về và cách đổi số tiền, theo tài liệu tích hợp VNPay. */
public final class VnpayCodes {
    /** vnp_ResponseCode / vnp_TransactionStatus: thành công. */
    public static final String SUCCESS = "00";
    /** vnp_TransactionStatus: giao dịch đã tạo nhưng khách chưa thanh toán xong. */
    public static final String UNFINISHED = "01";
    /** vnp_TransactionStatus: thanh toán thất bại. */
    public static final String FAILED = "02";
    /** vnp_ResponseCode của QueryDR: VNPay không tìm thấy đơn hàng. */
    public static final String ORDER_NOT_FOUND = "91";

    private VnpayCodes() { }

    /** VNPay gửi số tiền đã nhân 100 (ví dụ 15000000 nghĩa là 150.000 VND). */
    public static BigDecimal toVnd(String vnpayAmount) {
        return new BigDecimal(vnpayAmount).movePointLeft(2);
    }

    /** Lấy số tiền VND để gửi cho VNPay (nhân 100). */
    public static String fromVnd(long amount) {
        return Long.toString(Math.multiplyExact(amount, 100L));
    }
}
