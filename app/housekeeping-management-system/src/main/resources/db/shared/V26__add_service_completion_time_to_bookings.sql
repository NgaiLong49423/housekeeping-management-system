SET XACT_ABORT ON;

ALTER TABLE dbo.Bookings ADD service_completed_at DATETIME2(0) NULL;

-- Existing bookings get a best-effort completion timestamp. New completions are stamped
-- by the Staff workflow before payment; this backfill does not modify incident history.
EXEC sys.sp_executesql N'
    UPDATE booking
       SET service_completed_at = COALESCE(payment.paid_at, booking.end_time)
      FROM dbo.Bookings booking
      OUTER APPLY (
          SELECT MAX(paid_at) AS paid_at
            FROM dbo.Payments payment
           WHERE payment.booking_id=booking.booking_id AND payment.status=''SUCCESS''
      ) payment
     WHERE booking.status IN (''AWAITING_PAYMENT'',''COMPLETED'')
       AND booking.service_completed_at IS NULL;';
