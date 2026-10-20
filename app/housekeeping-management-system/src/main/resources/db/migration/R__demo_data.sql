-- Seed public demo accounts and sample bookings only when the demo profile is active.
IF '${demoDataEnabled}' = 'true'
BEGIN
    SET NOCOUNT ON;

    DECLARE @DemoPasswordHash NVARCHAR(255) = N'$2a$10$Q/H9aYiTWHEyy4wFTFt26.ZT2qBEKKlhkGYEfMRrH0dx53g1A3QnK';
    DECLARE @Now DATETIMEOFFSET(0) = SYSDATETIMEOFFSET();
    DECLARE @OffsetMinutes INT = DATEPART(TZOFFSET, @Now);
    DECLARE @Tomorrow DATETIMEOFFSET(0) = TODATETIMEOFFSET(
        DATEADD(HOUR, 9, CONVERT(DATETIME2(0), DATEADD(DAY, 1, CONVERT(DATE, @Now)))),
        @OffsetMinutes
    );
    DECLARE @PastDay DATETIMEOFFSET(0) = DATEADD(DAY, -10, @Tomorrow);

    IF NOT EXISTS (SELECT 1 FROM Customer WHERE email = N'customer@demo.gonnha.local')
        INSERT INTO Customer (display_name, email, password_hash, phone_number)
        VALUES (N'Khách hàng Demo', N'customer@demo.gonnha.local', @DemoPasswordHash, N'0900000001');

    IF NOT EXISTS (SELECT 1 FROM Staff WHERE email = N'staff@demo.gonnha.local')
        INSERT INTO Staff (display_name, email, password_hash, phone_number)
        VALUES (N'Nhân viên Demo', N'staff@demo.gonnha.local', @DemoPasswordHash, N'0900000002');

    IF NOT EXISTS (SELECT 1 FROM Admin WHERE email = N'admin@demo.gonnha.local')
        INSERT INTO Admin (display_name, email, password_hash)
        VALUES (N'Quản trị viên Demo', N'admin@demo.gonnha.local', @DemoPasswordHash);

    IF NOT EXISTS (SELECT 1 FROM Services WHERE service_name = N'Dọn dẹp nhà cơ bản')
        INSERT INTO Services (service_name, description, duration_minutes, base_price)
        VALUES (N'Dọn dẹp nhà cơ bản', N'Lau dọn các khu vực sinh hoạt chính.', 120, 150000);

    IF NOT EXISTS (SELECT 1 FROM Services WHERE service_name = N'Tổng vệ sinh')
        INSERT INTO Services (service_name, description, duration_minutes, base_price)
        VALUES (N'Tổng vệ sinh', N'Làm sạch kỹ các phòng và bề mặt trong nhà.', 240, 300000);

    IF NOT EXISTS (SELECT 1 FROM Services WHERE service_name = N'Vệ sinh nhà bếp')
        INSERT INTO Services (service_name, description, duration_minutes, base_price)
        VALUES (N'Vệ sinh nhà bếp', N'Làm sạch mặt bếp, bồn rửa và khu vực nấu ăn.', 90, 120000);

    DECLARE @CustomerId BIGINT = (SELECT customer_id FROM Customer WHERE email = N'customer@demo.gonnha.local');
    DECLARE @StaffId BIGINT = (SELECT staff_id FROM Staff WHERE email = N'staff@demo.gonnha.local');
    DECLARE @AdminId BIGINT = (SELECT admin_id FROM Admin WHERE email = N'admin@demo.gonnha.local');
    DECLARE @BasicServiceId BIGINT = (SELECT service_id FROM Services WHERE service_name = N'Dọn dẹp nhà cơ bản');
    DECLARE @DeepServiceId BIGINT = (SELECT service_id FROM Services WHERE service_name = N'Tổng vệ sinh');
    DECLARE @FutureScheduleId BIGINT;
    DECLARE @PastScheduleId BIGINT;
    DECLARE @UpcomingBookingId BIGINT;
    DECLARE @CompletedBookingId BIGINT;
    DECLARE @UpcomingStart DATETIMEOFFSET(0) = DATEADD(HOUR, 1, @Tomorrow);
    DECLARE @UpcomingEnd DATETIMEOFFSET(0) = DATEADD(HOUR, 3, @Tomorrow);
    DECLARE @CompletedStart DATETIMEOFFSET(0) = DATEADD(HOUR, 1, @PastDay);
    DECLARE @CompletedEnd DATETIMEOFFSET(0) = DATEADD(HOUR, 3, @PastDay);

    IF NOT EXISTS (SELECT 1 FROM Work_Schedule WHERE staff_id = @StaffId AND available_start = @Tomorrow)
        INSERT INTO Work_Schedule (staff_id, managed_by_admin_id, available_start, available_end)
        VALUES (@StaffId, @AdminId, @Tomorrow, DATEADD(HOUR, 5, @Tomorrow));

    IF NOT EXISTS (SELECT 1 FROM Work_Schedule WHERE staff_id = @StaffId AND available_start = @PastDay)
        INSERT INTO Work_Schedule (staff_id, managed_by_admin_id, available_start, available_end)
        VALUES (@StaffId, @AdminId, @PastDay, DATEADD(HOUR, 5, @PastDay));

    SELECT @FutureScheduleId = work_schedule_id
    FROM Work_Schedule
    WHERE staff_id = @StaffId AND available_start = @Tomorrow;

    SELECT @PastScheduleId = work_schedule_id
    FROM Work_Schedule
    WHERE staff_id = @StaffId AND available_start = @PastDay;

    IF NOT EXISTS (SELECT 1 FROM Booking WHERE customer_id = @CustomerId AND start_at = @UpcomingStart)
        INSERT INTO Booking (customer_id, start_at, end_at, booking_status, created_at)
        VALUES (@CustomerId, @UpcomingStart, @UpcomingEnd, 'CONFIRMED', @Now);

    IF NOT EXISTS (SELECT 1 FROM Booking WHERE customer_id = @CustomerId AND start_at = @CompletedStart)
        INSERT INTO Booking (customer_id, start_at, end_at, booking_status, created_at)
        VALUES (@CustomerId, @CompletedStart, @CompletedEnd, 'COMPLETED', @CompletedStart);

    SELECT @UpcomingBookingId = booking_id
    FROM Booking
    WHERE customer_id = @CustomerId AND start_at = @UpcomingStart;

    SELECT @CompletedBookingId = booking_id
    FROM Booking
    WHERE customer_id = @CustomerId AND start_at = @CompletedStart;

    IF NOT EXISTS (SELECT 1 FROM Booking_Detail WHERE booking_id = @UpcomingBookingId AND sequence_no = 1)
        INSERT INTO Booking_Detail
            (booking_id, work_schedule_id, service_id, sequence_no, duration_minutes_snapshot, unit_price_snapshot)
        VALUES (@UpcomingBookingId, @FutureScheduleId, @BasicServiceId, 1, 120, 150000);

    IF NOT EXISTS (SELECT 1 FROM Booking_Detail WHERE booking_id = @CompletedBookingId AND sequence_no = 1)
        INSERT INTO Booking_Detail
            (booking_id, work_schedule_id, service_id, sequence_no, duration_minutes_snapshot, unit_price_snapshot)
        VALUES (@CompletedBookingId, @PastScheduleId, @DeepServiceId, 1, 240, 300000);

    IF NOT EXISTS (SELECT 1 FROM Payment WHERE booking_id = @UpcomingBookingId)
        INSERT INTO Payment (booking_id, payment_method, payment_status, amount, created_at, verified_at)
        VALUES (@UpcomingBookingId, NULL, 'UNPAID', 150000, @Now, NULL);

    IF NOT EXISTS (SELECT 1 FROM Payment WHERE booking_id = @CompletedBookingId)
        INSERT INTO Payment (booking_id, payment_method, payment_status, amount, created_at, verified_at)
        VALUES (@CompletedBookingId, 'CASH', 'PAID', 300000, @CompletedStart, @CompletedEnd);

    IF NOT EXISTS (SELECT 1 FROM Feedback WHERE booking_id = @CompletedBookingId)
        INSERT INTO Feedback (booking_id, rating, comment, created_at)
        VALUES (@CompletedBookingId, 5, N'Dịch vụ tốt, nhân viên làm việc cẩn thận.', @CompletedEnd);

    IF NOT EXISTS (SELECT 1 FROM Favorite_Staff WHERE customer_id = @CustomerId AND staff_id = @StaffId)
        INSERT INTO Favorite_Staff (customer_id, staff_id, created_at)
        VALUES (@CustomerId, @StaffId, @Now);
END;
