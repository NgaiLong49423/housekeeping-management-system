package com.housekeeping.management.repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentPersistenceRepository {
    private final JdbcTemplate jdbc;

    public PaymentPersistenceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Long nextPayosOrderCode() {
        return jdbc.queryForObject("SELECT NEXT VALUE FOR dbo.PayOS_Order_Code_Seq", Long.class);
    }

    public List<PaymentState> findForBookingUpdate(Long bookingId) {
        return jdbc.query("""
                SELECT payment_id, booking_id, amount, payment_method, status, transaction_code
                FROM dbo.Payments WITH (UPDLOCK, HOLDLOCK, INDEX(IX_Payments_Booking_Status))
                WHERE booking_id=? ORDER BY created_at DESC, payment_id DESC
                """, (rs, row) -> new PaymentState(rs.getLong("payment_id"), rs.getLong("booking_id"),
                rs.getBigDecimal("amount"), rs.getString("payment_method"), rs.getString("status"),
                rs.getString("transaction_code")), bookingId);
    }

    public Optional<PaymentState> findByOrderCodeForUpdate(Long orderCode) {
        return jdbc.query("""
                SELECT payment_id, booking_id, amount, payment_method, status, transaction_code
                FROM dbo.Payments WITH (UPDLOCK, HOLDLOCK)
                WHERE transaction_code=?
                """, (rs, row) -> new PaymentState(rs.getLong("payment_id"), rs.getLong("booking_id"),
                rs.getBigDecimal("amount"), rs.getString("payment_method"), rs.getString("status"),
                rs.getString("transaction_code")), Long.toString(orderCode)).stream().findFirst();
    }

    public Optional<PaymentState> findByOrderCode(Long orderCode) {
        return jdbc.query("""
                SELECT payment_id, booking_id, amount, payment_method, status, transaction_code
                FROM dbo.Payments WHERE transaction_code=?
                """, (rs, row) -> new PaymentState(rs.getLong("payment_id"), rs.getLong("booking_id"),
                rs.getBigDecimal("amount"), rs.getString("payment_method"), rs.getString("status"),
                rs.getString("transaction_code")), Long.toString(orderCode)).stream().findFirst();
    }

    public Long insertPendingOnline(Long bookingId, BigDecimal amount, long orderCode, LocalDateTime createdAt) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO dbo.Payments (booking_id, amount, payment_method, status, transaction_code, created_at)
                    VALUES (?, ?, 'ONLINE', 'PENDING', ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, bookingId);
            statement.setBigDecimal(2, amount);
            statement.setString(3, Long.toString(orderCode));
            statement.setObject(4, createdAt);
            return statement;
        }, keys);
        Number key = keys.getKey();
        if (key == null) throw new IllegalStateException("Không lấy được mã lần thanh toán vừa tạo.");
        return key.longValue();
    }

    public Long insertCashSuccess(Long bookingId, BigDecimal amount, LocalDateTime paidAt) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO dbo.Payments (booking_id, amount, payment_method, status, paid_at, created_at)
                    VALUES (?, ?, 'CASH', 'SUCCESS', ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, bookingId);
            statement.setBigDecimal(2, amount);
            statement.setObject(3, paidAt);
            statement.setObject(4, paidAt);
            return statement;
        }, keys);
        Number key = keys.getKey();
        if (key == null) throw new IllegalStateException("Không lấy được mã thanh toán tiền mặt vừa tạo.");
        return key.longValue();
    }

    public boolean hasSuccessfulPayment(Long bookingId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Payments WHERE booking_id=? AND status='SUCCESS'",
                Integer.class, bookingId);
        return count != null && count > 0;
    }

    public int failPendingOnlineAttempts(Long bookingId) {
        return jdbc.update("UPDATE dbo.Payments SET status='FAILED' WHERE booking_id=? AND payment_method='ONLINE' AND status='PENDING'",
                bookingId);
    }

    public int markSuccess(Long paymentId, LocalDateTime paidAt) {
        return jdbc.update("UPDATE dbo.Payments SET status='SUCCESS', paid_at=? WHERE payment_id=? AND payment_method='ONLINE' AND status IN ('PENDING','FAILED')",
                paidAt, paymentId);
    }

    public int markFailed(Long paymentId) {
        return jdbc.update("UPDATE dbo.Payments SET status='FAILED' WHERE payment_id=? AND payment_method='ONLINE' AND status='PENDING'",
                paymentId);
    }

    public int completeBooking(Long bookingId) {
        return jdbc.update("UPDATE dbo.Bookings SET status='COMPLETED' WHERE booking_id=? AND status='AWAITING_PAYMENT'",
                bookingId);
    }

    public record PaymentState(Long paymentId, Long bookingId, BigDecimal amount,
            String method, String status, String transactionCode) { }
}
