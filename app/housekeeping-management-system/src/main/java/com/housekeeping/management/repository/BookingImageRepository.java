package com.housekeeping.management.repository;

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
public class BookingImageRepository {
    private final JdbcTemplate jdbc;

    public BookingImageRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<BookingAccess> lockBooking(Long bookingId) {
        List<BookingAccess> rows = jdbc.query("""
                SELECT staff_id, status FROM dbo.Bookings WITH (UPDLOCK, HOLDLOCK)
                WHERE booking_id=?
                """, (rs, row) -> new BookingAccess(rs.getLong("staff_id"), rs.getString("status")), bookingId);
        return rows.stream().findFirst();
    }

    public Long insertImage(Long bookingId, String imageUrl, LocalDateTime uploadedAt) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO dbo.Booking_Images (booking_id, image_url, uploaded_at) VALUES (?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, bookingId);
            statement.setString(2, imageUrl);
            statement.setObject(3, uploadedAt);
            return statement;
        }, keys);
        Number key = keys.getKey();
        if (key == null) {
            throw new IllegalStateException("SQL Server did not return the new booking image ID.");
        }
        return key.longValue();
    }

    public List<String> findAllImageUrls() {
        return jdbc.query("SELECT image_url FROM dbo.Booking_Images", (rs, row) -> rs.getString(1));
    }

    public record BookingAccess(Long staffId, String status) { }
}
