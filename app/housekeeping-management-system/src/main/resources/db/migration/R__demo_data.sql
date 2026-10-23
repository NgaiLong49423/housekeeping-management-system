-- Seed public demo accounts and sample bookings only when the demo profile is active.
IF '${demoDataEnabled}' = 'true'
BEGIN
    SET NOCOUNT ON;

    DECLARE @DemoPasswordHash NVARCHAR(255) = N'$2a$10$Q/H9aYiTWHEyy4wFTFt26.ZT2qBEKKlhkGYEfMRrH0dx53g1A3QnK';
    DECLARE @Now DATETIMEOFFSET(0) = SYSDATETIMEOFFSET();
    DECLARE @OffsetMinutes INT = DATEPART(TZOFFSET, @Now);
    DECLARE @Today DATETIME2(0) = CONVERT(DATETIME2(0), CONVERT(DATE, @Now));

    DECLARE @DemoCustomers TABLE (
        email NVARCHAR(254) COLLATE Latin1_General_100_CI_AS NOT NULL PRIMARY KEY,
        display_name NVARCHAR(150) NOT NULL,
        phone_number NVARCHAR(20) NOT NULL
    );
    INSERT INTO @DemoCustomers (email, display_name, phone_number)
    VALUES
        (N'customer@demo.gonnha.local', N'Khách hàng Demo', N'0900000001'),
        (N'customer01@demo.gonnha.local', N'Nguyễn Minh Anh', N'0900000011'),
        (N'customer02@demo.gonnha.local', N'Trần Quốc Bảo', N'0900000012'),
        (N'customer03@demo.gonnha.local', N'Lê Thu Hà', N'0900000013'),
        (N'customer04@demo.gonnha.local', N'Phạm Gia Huy', N'0900000014'),
        (N'customer05@demo.gonnha.local', N'Võ Ngọc Lan', N'0900000015'),
        (N'customer06@demo.gonnha.local', N'Đặng Hoàng Nam', N'0900000016'),
        (N'customer07@demo.gonnha.local', N'Bùi Khánh Vy', N'0900000017');

    INSERT INTO Customer (display_name, email, password_hash, phone_number)
    SELECT d.display_name, d.email, @DemoPasswordHash, d.phone_number
    FROM @DemoCustomers d
    WHERE NOT EXISTS (SELECT 1 FROM Customer c WHERE c.email = d.email);

    DECLARE @DemoStaff TABLE (
        email NVARCHAR(254) COLLATE Latin1_General_100_CI_AS NOT NULL PRIMARY KEY,
        display_name NVARCHAR(150) NOT NULL,
        phone_number NVARCHAR(20) NOT NULL
    );
    INSERT INTO @DemoStaff (email, display_name, phone_number)
    VALUES
        (N'staff@demo.gonnha.local', N'Nhân viên Demo', N'0900000002'),
        (N'staff01@demo.gonnha.local', N'Nguyễn Văn An', N'0900000021'),
        (N'staff02@demo.gonnha.local', N'Trần Thị Bình', N'0900000022'),
        (N'staff03@demo.gonnha.local', N'Lê Hoàng Cường', N'0900000023'),
        (N'staff04@demo.gonnha.local', N'Phạm Thị Dung', N'0900000024'),
        (N'staff05@demo.gonnha.local', N'Võ Minh Đức', N'0900000025'),
        (N'staff06@demo.gonnha.local', N'Đặng Thị Giang', N'0900000026'),
        (N'staff07@demo.gonnha.local', N'Bùi Quốc Khánh', N'0900000027');

    INSERT INTO Staff (display_name, email, password_hash, phone_number)
    SELECT d.display_name, d.email, @DemoPasswordHash, d.phone_number
    FROM @DemoStaff d
    WHERE NOT EXISTS (SELECT 1 FROM Staff s WHERE s.email = d.email);

    IF NOT EXISTS (SELECT 1 FROM Admin WHERE email = N'admin@demo.gonnha.local')
        INSERT INTO Admin (display_name, email, password_hash)
        VALUES (N'Quản trị viên Demo', N'admin@demo.gonnha.local', @DemoPasswordHash);

    DECLARE @DemoServices TABLE (
        service_name NVARCHAR(150) NOT NULL PRIMARY KEY,
        description NVARCHAR(1000) NOT NULL,
        duration_minutes INT NOT NULL,
        base_price DECIMAL(18, 0) NOT NULL
    );
    INSERT INTO @DemoServices (service_name, description, duration_minutes, base_price)
    VALUES
        (N'Dọn dẹp nhà cơ bản', N'Lau dọn các khu vực sinh hoạt chính.', 120, 150000),
        (N'Tổng vệ sinh', N'Làm sạch kỹ các phòng và bề mặt trong nhà.', 240, 300000),
        (N'Vệ sinh nhà bếp', N'Làm sạch mặt bếp, bồn rửa và khu vực nấu ăn.', 90, 120000),
        (N'Vệ sinh phòng tắm', N'Cọ rửa thiết bị và làm sạch khu vực phòng tắm.', 90, 130000),
        (N'Giặt sofa tại nhà', N'Làm sạch sofa và ghế bọc vải tại nhà.', 150, 250000),
        (N'Lau kính và cửa sổ', N'Lau sạch kính và khung cửa sổ trong nhà.', 120, 180000),
        (N'Dọn dẹp căn hộ nhỏ', N'Dọn dẹp căn hộ studio hoặc căn hộ một phòng ngủ.', 120, 160000),
        (N'Vệ sinh sau sửa chữa', N'Làm sạch bụi bẩn sau khi sửa chữa nhà.', 240, 350000);

    INSERT INTO Services (service_name, description, duration_minutes, base_price)
    SELECT d.service_name, d.description, d.duration_minutes, d.base_price
    FROM @DemoServices d
    WHERE NOT EXISTS (SELECT 1 FROM Services s WHERE s.service_name = d.service_name);

    DECLARE @AdminId BIGINT = (SELECT admin_id FROM Admin WHERE email = N'admin@demo.gonnha.local');
    DECLARE @DemoBookings TABLE (
        customer_email NVARCHAR(254) COLLATE Latin1_General_100_CI_AS NOT NULL,
        staff_email NVARCHAR(254) COLLATE Latin1_General_100_CI_AS NOT NULL,
        day_offset INT NOT NULL,
        booking_hour INT NOT NULL,
        service_name NVARCHAR(150) NOT NULL,
        booking_status VARCHAR(20) NOT NULL,
        payment_status VARCHAR(10) NULL,
        payment_method VARCHAR(20) NULL,
        rating TINYINT NULL,
        feedback_comment NVARCHAR(2000) NULL,
        PRIMARY KEY (customer_email, day_offset)
    );
    INSERT INTO @DemoBookings
        (customer_email, staff_email, day_offset, booking_hour, service_name, booking_status,
         payment_status, payment_method, rating, feedback_comment)
    VALUES
        (N'customer@demo.gonnha.local', N'staff@demo.gonnha.local',  1, 10, N'Dọn dẹp nhà cơ bản', 'CONFIRMED', NULL, NULL, NULL, NULL),
        (N'customer01@demo.gonnha.local', N'staff01@demo.gonnha.local',  2, 10, N'Tổng vệ sinh', 'CONFIRMED', NULL, NULL, NULL, NULL),
        (N'customer02@demo.gonnha.local', N'staff02@demo.gonnha.local',  3, 10, N'Vệ sinh nhà bếp', 'CONFIRMED', NULL, NULL, NULL, NULL),
        (N'customer03@demo.gonnha.local', N'staff03@demo.gonnha.local',  4, 10, N'Vệ sinh phòng tắm', 'CONFIRMED', NULL, NULL, NULL, NULL),
        (N'customer04@demo.gonnha.local', N'staff04@demo.gonnha.local',  5, 10, N'Giặt sofa tại nhà', 'CONFIRMED', NULL, NULL, NULL, NULL),
        (N'customer05@demo.gonnha.local', N'staff05@demo.gonnha.local',  6, 10, N'Lau kính và cửa sổ', 'CONFIRMED', NULL, NULL, NULL, NULL),
        (N'customer06@demo.gonnha.local', N'staff06@demo.gonnha.local',  7, 10, N'Dọn dẹp căn hộ nhỏ', 'CANCELLED', NULL, NULL, NULL, NULL),
        (N'customer07@demo.gonnha.local', N'staff07@demo.gonnha.local',  8, 10, N'Vệ sinh sau sửa chữa', 'CANCELLED', NULL, NULL, NULL, NULL),
        (N'customer01@demo.gonnha.local', N'staff02@demo.gonnha.local',  9, 10, N'Dọn dẹp nhà cơ bản', 'CANCELLED', NULL, NULL, NULL, NULL),
        (N'customer02@demo.gonnha.local', N'staff03@demo.gonnha.local', -1, 10, N'Tổng vệ sinh', 'COMPLETED', 'PAID', 'BANK_TRANSFER', 5, N'Nhà sạch sẽ, nhân viên làm việc đúng giờ.'),
        (N'customer03@demo.gonnha.local', N'staff04@demo.gonnha.local', -2, 10, N'Vệ sinh nhà bếp', 'COMPLETED', 'UNPAID', 'BANK_TRANSFER', 4, N'Khu vực bếp được làm sạch cẩn thận.'),
        (N'customer04@demo.gonnha.local', N'staff05@demo.gonnha.local', -3, 10, N'Vệ sinh phòng tắm', 'COMPLETED', 'PAID', 'CASH', 5, N'Dịch vụ tốt, phòng tắm sạch hơn nhiều.'),
        (N'customer05@demo.gonnha.local', N'staff06@demo.gonnha.local', -4, 10, N'Giặt sofa tại nhà', 'COMPLETED', 'UNPAID', 'CASH', NULL, NULL),
        (N'customer06@demo.gonnha.local', N'staff07@demo.gonnha.local', -5, 10, N'Lau kính và cửa sổ', 'COMPLETED', 'PAID', 'BANK_TRANSFER', 4, N'Kính sạch và sáng, cảm ơn nhân viên.'),
        (N'customer07@demo.gonnha.local', N'staff01@demo.gonnha.local', -6, 10, N'Dọn dẹp căn hộ nhỏ', 'COMPLETED', 'PAID', 'CASH', NULL, NULL),
        (N'customer@demo.gonnha.local', N'staff03@demo.gonnha.local', -7, 10, N'Vệ sinh sau sửa chữa', 'COMPLETED', 'UNPAID', NULL, 5, N'Nhân viên hỗ trợ nhiệt tình.'),
        (N'customer01@demo.gonnha.local', N'staff05@demo.gonnha.local', -8, 10, N'Dọn dẹp nhà cơ bản', 'COMPLETED', 'PAID', 'BANK_TRANSFER', NULL, NULL),
        (N'customer@demo.gonnha.local', N'staff@demo.gonnha.local',  0, 10, N'Tổng vệ sinh', 'COMPLETED', 'UNPAID', NULL, NULL, NULL),
        (N'customer02@demo.gonnha.local', N'staff07@demo.gonnha.local', -10, 10, N'Vệ sinh nhà bếp', 'COMPLETED', 'PAID', 'CASH', NULL, NULL),
        (N'customer03@demo.gonnha.local', N'staff@demo.gonnha.local', -11, 10, N'Giặt sofa tại nhà', 'COMPLETED', 'UNPAID', 'BANK_TRANSFER', 4, N'Sofa sạch, nhân viên tư vấn dễ hiểu.');

    DECLARE @ResolvedBookings TABLE (
        customer_id BIGINT NOT NULL,
        staff_id BIGINT NOT NULL,
        booking_start DATETIMEOFFSET(0) NOT NULL,
        booking_end DATETIMEOFFSET(0) NOT NULL,
        schedule_start DATETIMEOFFSET(0) NOT NULL,
        schedule_end DATETIMEOFFSET(0) NOT NULL,
        service_id BIGINT NOT NULL,
        duration_minutes INT NOT NULL,
        unit_price DECIMAL(18, 0) NOT NULL,
        booking_status VARCHAR(20) NOT NULL,
        payment_status VARCHAR(10) NULL,
        payment_method VARCHAR(20) NULL,
        rating TINYINT NULL,
        feedback_comment NVARCHAR(2000) NULL,
        PRIMARY KEY (customer_id, booking_start)
    );

    INSERT INTO @ResolvedBookings
    SELECT c.customer_id,
           s.staff_id,
           TODATETIMEOFFSET(DATEADD(HOUR, d.booking_hour, DATEADD(DAY, d.day_offset, @Today)), @OffsetMinutes),
           TODATETIMEOFFSET(DATEADD(MINUTE, svc.duration_minutes,
               DATEADD(HOUR, d.booking_hour, DATEADD(DAY, d.day_offset, @Today))), @OffsetMinutes),
           TODATETIMEOFFSET(DATEADD(HOUR, d.booking_hour - 1, DATEADD(DAY, d.day_offset, @Today)), @OffsetMinutes),
           TODATETIMEOFFSET(DATEADD(HOUR, d.booking_hour + 4, DATEADD(DAY, d.day_offset, @Today)), @OffsetMinutes),
           svc.service_id, svc.duration_minutes, svc.base_price, d.booking_status,
           d.payment_status, d.payment_method, d.rating, d.feedback_comment
    FROM @DemoBookings d
    JOIN Customer c ON c.email = d.customer_email
    JOIN Staff s ON s.email = d.staff_email
    JOIN Services svc ON svc.service_name = d.service_name;

    INSERT INTO Work_Schedule (staff_id, managed_by_admin_id, available_start, available_end)
    SELECT d.staff_id, @AdminId, d.schedule_start, d.schedule_end
    FROM @ResolvedBookings d
    WHERE NOT EXISTS (
        SELECT 1 FROM Work_Schedule w
        WHERE w.staff_id = d.staff_id AND w.available_start = d.schedule_start
    );

    INSERT INTO Booking (customer_id, start_at, end_at, booking_status, created_at)
    SELECT d.customer_id, d.booking_start, d.booking_end, d.booking_status,
           CASE WHEN d.booking_status = 'COMPLETED' THEN d.booking_start ELSE @Now END
    FROM @ResolvedBookings d
    WHERE NOT EXISTS (
        SELECT 1 FROM Booking b
        WHERE b.customer_id = d.customer_id AND b.start_at = d.booking_start
    );

    INSERT INTO Booking_Detail
        (booking_id, work_schedule_id, service_id, sequence_no, duration_minutes_snapshot, unit_price_snapshot)
    SELECT b.booking_id, w.work_schedule_id, d.service_id, 1, d.duration_minutes, d.unit_price
    FROM @ResolvedBookings d
    JOIN Booking b ON b.customer_id = d.customer_id AND b.start_at = d.booking_start
    JOIN Work_Schedule w ON w.staff_id = d.staff_id AND w.available_start = d.schedule_start
    WHERE NOT EXISTS (
        SELECT 1 FROM Booking_Detail bd WHERE bd.booking_id = b.booking_id AND bd.sequence_no = 1
    );

    INSERT INTO Payment (booking_id, payment_method, payment_status, amount, created_at, verified_at)
    SELECT b.booking_id, d.payment_method, d.payment_status,
           d.unit_price,
           CASE WHEN d.booking_status = 'COMPLETED' THEN d.booking_end ELSE @Now END,
           CASE WHEN d.payment_status = 'PAID' THEN DATEADD(MINUTE, 30, d.booking_end) ELSE NULL END
    FROM @ResolvedBookings d
    JOIN Booking b ON b.customer_id = d.customer_id AND b.start_at = d.booking_start
    WHERE d.payment_status IS NOT NULL
      AND NOT EXISTS (SELECT 1 FROM Payment p WHERE p.booking_id = b.booking_id);

    INSERT INTO Feedback (booking_id, rating, comment, created_at)
    SELECT b.booking_id, d.rating, d.feedback_comment, DATEADD(MINUTE, 35, d.booking_end)
    FROM @ResolvedBookings d
    JOIN Booking b ON b.customer_id = d.customer_id AND b.start_at = d.booking_start
    WHERE d.rating IS NOT NULL AND d.feedback_comment IS NOT NULL
      AND NOT EXISTS (SELECT 1 FROM Feedback f WHERE f.booking_id = b.booking_id);

    INSERT INTO Favorite_Staff (customer_id, staff_id, created_at)
    SELECT c.customer_id, s.staff_id, @Now
    FROM (VALUES
        (N'customer@demo.gonnha.local', N'staff@demo.gonnha.local'),
        (N'customer01@demo.gonnha.local', N'staff01@demo.gonnha.local'),
        (N'customer02@demo.gonnha.local', N'staff02@demo.gonnha.local'),
        (N'customer03@demo.gonnha.local', N'staff03@demo.gonnha.local'),
        (N'customer04@demo.gonnha.local', N'staff04@demo.gonnha.local'),
        (N'customer05@demo.gonnha.local', N'staff05@demo.gonnha.local'),
        (N'customer06@demo.gonnha.local', N'staff06@demo.gonnha.local'),
        (N'customer07@demo.gonnha.local', N'staff07@demo.gonnha.local')
    ) favorites(customer_email, staff_email)
    JOIN Customer c ON c.email = favorites.customer_email COLLATE Latin1_General_100_CI_AS
    JOIN Staff s ON s.email = favorites.staff_email COLLATE Latin1_General_100_CI_AS
    WHERE NOT EXISTS (
        SELECT 1 FROM Favorite_Staff f WHERE f.customer_id = c.customer_id AND f.staff_id = s.staff_id
    );
END;
