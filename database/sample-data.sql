-- ====================================================================================
-- HOUSEKEEPING MANAGEMENT SYSTEM - COMPLETE SEED & FIXTURE SCRIPT
-- Database: Housekeeping_Database
-- All accounts use Password: Password123!
-- Safe, idempotent execution (IF NOT EXISTS)
-- ====================================================================================
SET XACT_ABORT ON;
BEGIN TRANSACTION;

-- BCrypt hash của mật khẩu: Password123!
DECLARE @PassHash NVARCHAR(255) = N'{bcrypt}$2a$10$Y85jiA/BKR2TZlb/1kHPWeL79WzLTeWZgv4jiNZHsAWWDjl/qWFoO';

-- 2. CẤU HÌNH DOANH NGHIỆP (Company_Settings)
IF NOT EXISTS (SELECT 1 FROM dbo.Company_Settings)
BEGIN
    INSERT INTO dbo.Company_Settings (company_address, latitude, longitude, service_radius_km)
    VALUES (N'123 Nguyễn Huệ, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh', 10.7769, 106.7009, 25.00);
END;

-- 3. SEED 10 DỊCH VỤ CHUẨN (Services)
DECLARE @Services TABLE (
    service_name NVARCHAR(100),
    description NVARCHAR(500),
    image_url NVARCHAR(500),
    price DECIMAL(12,2),
    duration_minutes INT
);

INSERT INTO @Services (service_name, description, image_url, price, duration_minutes) VALUES
(N'Dọn dẹp nhà cơ bản', N'Quét dọn, lau sàn, hút bụi, lau bàn ghế và sắp xếp đồ đạc phòng khách, phòng ngủ.', N'/images/services/don-nha-co-ban.jpg', 150000.00, 120),
(N'Tổng vệ sinh toàn diện', N'Vệ sinh chuyên sâu toàn bộ ngôi nhà từ trên xuống dưới, làm sạch cửa kính, trần nhà và các góc khuất.', N'/images/services/tong-ve-sinh.jpg', 300000.00, 240),
(N'Vệ sinh nhà bếp & thiết bị', N'Tẩy dầu mỡ bếp ga, bếp từ, lau máy hút mùi, cọ rửa bồn rửa bát và làm sạch lò vi sóng, tủ lạnh.', N'/images/services/ve-sinh-bep.jpg', 120000.00, 90),
(N'Vệ sinh sau sửa chữa', N'Thu dọn rác xây dựng nhỏ, tẩy vết sơn, xi măng bám trên sàn gạch và kính, hút bụi mịn công nghiệp.', N'/images/services/sau-sua-chua.jpg', 350000.00, 240),
(N'Vệ sinh toilet & phòng tắm chuyên sâu', N'Tẩy ố vàng gạch men, khử cặn canxi trên vách kính, tẩy trắng bồn cầu và khử trùng bằng tia cực tím.', N'/images/services/ve-sinh-toilet.jpg', 130000.00, 90),
(N'Giặt hấp sofa & đệm ngủ tại nhà', N'Giặt hơi nước nóng 140 độ C diệt khuẩn nấm mốc, hút sâu bọ rệp và khử mùi hôi cho sofa vải, nệm cao su.', N'/images/services/giat-sofa.jpg', 280000.00, 150),
(N'Vệ sinh máy lạnh & lưới lọc bụi', N'Xịt rửa dàn lạnh, dàn nóng, vệ sinh lưới lọc bụi kháng khuẩn, thông đường ống thoát nước máy lạnh.', N'/images/services/ve-sinh-may-lanh.jpg', 160000.00, 60),
(N'Ủi đồ & Chăm sóc trang phục', N'Ủi phẳng quần tây, áo sơ mi, váy đầm và phân loại, treo gấp trang phục gọn gàng vào tủ đồ.', N'/images/services/ui-do.jpg', 100000.00, 60),
(N'Khử khuẩn & Khử mùi sinh học Nano', N'Phun khử khuẩn Nano bạc toàn bộ không gian phòng, tiêu diệt 99.9% vi khuẩn, virus và mùi ẩm mốc lâu ngày.', N'/images/services/khu-khuan.jpg', 200000.00, 90),
(N'Dọn dẹp & Chăm sóc ban công sân vườn', N'Quét dọn lá cây, cọ rửa sàn ban công, tưới cây, lau chùi lan can và sắp xếp bàn trà thư giãn.', N'/images/services/ban-cong.jpg', 140000.00, 90);

INSERT INTO dbo.Services (service_name, description, image_url, price, duration_minutes, status)
SELECT s.service_name, s.description, s.image_url, s.price, s.duration_minutes, 'ACTIVE'
FROM @Services s
WHERE NOT EXISTS (SELECT 1 FROM dbo.Services existing WHERE existing.service_name = s.service_name);

-- 4. SEED TÀI KHOẢN HỆ THỐNG CHUẨN (Password: Password123!)
-- 4.1. Manager (Quản lý cấp cao)
IF NOT EXISTS (SELECT 1 FROM dbo.Users WHERE email = N'manager@gonnha.vn')
BEGIN
    INSERT INTO dbo.Users (full_name, email, password_hash, phone, role, status)
    VALUES (N'Quản lý cấp cao', N'manager@gonnha.vn', @PassHash, N'0900000005', 'MANAGER', 'ACTIVE');
END;

-- 4.2. Admin (Quản trị viên)
IF NOT EXISTS (SELECT 1 FROM dbo.Users WHERE email = N'admin@gonnha.vn')
BEGIN
    INSERT INTO dbo.Users (full_name, email, password_hash, phone, role, status)
    VALUES (N'Quản trị viên hệ thống', N'admin@gonnha.vn', @PassHash, N'0900000001', 'ADMIN', 'ACTIVE');
END;

-- 4.3. Customer (Khách hàng)
IF NOT EXISTS (SELECT 1 FROM dbo.Users WHERE email = N'customer@gonnha.vn')
BEGIN
    INSERT INTO dbo.Users (full_name, email, password_hash, phone, role, status)
    VALUES (N'Cao Kỳ Nguyễn Hà', N'customer@gonnha.vn', @PassHash, N'0787523002', 'CUSTOMER', 'ACTIVE');
END;

IF NOT EXISTS (SELECT 1 FROM dbo.Users WHERE email = N'nguyenhacaoky@gmail.com')
BEGIN
    INSERT INTO dbo.Users (full_name, email, password_hash, phone, role, status)
    VALUES (N'Cao Kỳ Nguyễn Hà', N'nguyenhacaoky@gmail.com', @PassHash, N'0787523002', 'CUSTOMER', 'ACTIVE');
END;

-- 5. SEED 10 NHÂN VIÊN (STAFF)
DECLARE @StaffList TABLE (
    full_name NVARCHAR(150),
    email NVARCHAR(255) COLLATE Latin1_General_100_CI_AS,
    phone NVARCHAR(20),
    staff_type VARCHAR(20),
    avatar_url NVARCHAR(500),
    bio NVARCHAR(MAX)
);

INSERT INTO @StaffList (full_name, email, phone, staff_type, avatar_url, bio) VALUES
(N'Nguyễn Thị Mai', N'mai.nguyen@gonnha.vn', N'0901234561', 'FULL_TIME',
 N'https://images.unsplash.com/photo-1544005313-94ddf0286df2?w=200',
 N'Hơn 3 năm kinh nghiệm dọn dẹp nhà ở và căn hộ chung cư cao cấp. Tận tâm, chu đáo, làm việc cẩn thận và ngăn nắp.'),
(N'Trần Văn Nam', N'nam.tran@gonnha.vn', N'0901234562', 'PART_TIME',
 N'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=200',
 N'Nhiệt tình, trung thực, chuyên sâu vệ sinh nhà bếp và phòng tắm. Luôn hoàn thành đúng hẹn và đúng yêu cầu của gia chủ.'),
(N'Lê Hoàng Cường', N'cuong.le@gonnha.vn', N'0901234563', 'FULL_TIME',
 N'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=200',
 N'Kinh nghiệm tổng vệ sinh nhà mới sửa chữa, tẩy ố kính, lau chùi nội thất gỗ kỹ lưỡng.'),
(N'Phạm Thị Dung', N'dung.pham@gonnha.vn', N'0901234564', 'PART_TIME',
 N'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200',
 N'Tỉ mỉ, khéo tay, có kỹ năng sắp xếp đồ đạc ngăn nắp, gọn gàng và tạo không gian sống thoải mái.'),
(N'Hoàng Thị Lan', N'lan.hoang@gonnha.vn', N'0900000014', 'FULL_TIME',
 N'https://images.unsplash.com/photo-1544005313-94ddf0286df2?w=200',
 N'Chuyên nghiệp, chu đáo, có hơn 4 năm kinh nghiệm vệ sinh căn hộ cao cấp và biệt thự.'),
(N'Vũ Anh Tuấn', N'tuan.vu@gonnha.vn', N'0900000015', 'PART_TIME',
 N'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=200',
 N'Nhanh nhẹn, cẩn thận, chuyên dọn dẹp nhà sau sửa chữa, khử khuẩn và sắp xếp kho đồ.'),
(N'Đỗ Quỳnh Hoa', N'hoa.do@gonnha.vn', N'0900000016', 'FULL_TIME',
 N'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200',
 N'Tỉ mỉ, khéo léo, am hiểu các kỹ thuật làm sạch đồ gia dụng, khử mùi nhà bếp và tẩy ố đá tự nhiên.'),
(N'Nguyễn Đức Huy', N'huy.nguyen@gonnha.vn', N'0900000017', 'PART_TIME',
 N'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=200',
 N'Chăm chỉ, đúng giờ, chuyên vệ sinh sàn đá, đánh bóng kính và lau dọn ban công, sân vườn.'),
(N'Lê Minh Thư', N'thu.le@gonnha.vn', N'0900000018', 'FULL_TIME',
 N'https://images.unsplash.com/photo-1517841905240-472988babdf9?w=200',
 N'Thân thiện, chu đáo, được nhiều khách hàng tin cậy đánh giá 5 sao về độ sạch sẽ và ngăn nắp.'),
(N'Trần Thanh Phong', N'phong.tran@gonnha.vn', N'0900000019', 'FULL_TIME',
 N'https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?w=200',
 N'Trách nhiệm cao, tác phong nhanh nhẹn, chuyên giặt hấp sofa, thảm trải sàn và nệm cao su.');

DECLARE @fn NVARCHAR(150), @em NVARCHAR(255), @ph NVARCHAR(20), @st VARCHAR(20), @av NVARCHAR(500), @bi NVARCHAR(MAX);
DECLARE staff_cursor CURSOR FOR SELECT full_name, email, phone, staff_type, avatar_url, bio FROM @StaffList;
OPEN staff_cursor;
FETCH NEXT FROM staff_cursor INTO @fn, @em, @ph, @st, @av, @bi;

WHILE @@FETCH_STATUS = 0
BEGIN
    DECLARE @Uid BIGINT;
    SELECT @Uid = user_id FROM dbo.Users WHERE email = @em;
    IF @Uid IS NULL
    BEGIN
        INSERT INTO dbo.Users (full_name, email, password_hash, phone, role, status)
        VALUES (@fn, @em, @PassHash, @ph, 'STAFF', 'ACTIVE');
        SET @Uid = SCOPE_IDENTITY();
    END

    DECLARE @Sid BIGINT;
    SELECT @Sid = staff_id FROM dbo.Staff WHERE user_id = @Uid;
    IF @Sid IS NULL
    BEGIN
        INSERT INTO dbo.Staff (user_id, staff_type) VALUES (@Uid, @st);
        SET @Sid = SCOPE_IDENTITY();
    END

    IF NOT EXISTS (SELECT 1 FROM dbo.Staff_Profiles WHERE staff_id = @Sid)
    BEGIN
        INSERT INTO dbo.Staff_Profiles (staff_id, bio, avatar_url)
        VALUES (@Sid, @bi, @av);
    END

    FETCH NEXT FROM staff_cursor INTO @fn, @em, @ph, @st, @av, @bi;
END;
CLOSE staff_cursor;
DEALLOCATE staff_cursor;

-- 6. SEED ĐỊA CHỈ NHÀ MẪU CHO CUSTOMER (Houses)
DECLARE @CustId BIGINT;
SELECT @CustId = user_id FROM dbo.Users WHERE email = N'customer@gonnha.vn';
IF @CustId IS NOT NULL AND NOT EXISTS (SELECT 1 FROM dbo.Houses WHERE customer_id = @CustId)
BEGIN
    INSERT INTO dbo.Houses (customer_id, house_name, address, latitude, longitude, address_note, is_deleted, recipient_name, recipient_phone, province_city, ward_commune, address_detail, address_type, is_default)
    VALUES
    (@CustId, N'Nhà riêng VinHomes', N'Bs16 VinHome Grand Park, Phường Long Bình, TP. Thủ Đức, TP. Hồ Chí Minh', 10.8456, 106.8422, N'Căn hộ tầng 12, bấm chuông 1205', 0, N'Cao Kỳ Nguyễn Hà', N'0787523002', N'TP. Hồ Chí Minh', N'Phường Long Bình', N'Bs16 VinHome Grand Park', N'APARTMENT', 1),
    (@CustId, N'Nhà riêng Quy Nhơn', N'01 Đường Ngô Mây, Phường Ngô Mây, TP. Quy Nhơn, Tỉnh Bình Định', 13.7654, 109.2234, N'Cổng màu xanh', 0, N'Cao Kỳ Nguyễn Hà', N'0787523002', N'Tỉnh Bình Định', N'Phường Ngô Mây', N'01 Đường Ngô Mây', N'HOUSE', 0);
END;

-- 7. SEED NHÂN VIÊN YÊU THÍCH CHO CUSTOMER (Staff_Follows)
IF @CustId IS NOT NULL
BEGIN
    DECLARE @MaiStaffId BIGINT, @NamStaffId BIGINT;
    SELECT @MaiStaffId = staff_id FROM dbo.Staff WHERE user_id = (SELECT user_id FROM dbo.Users WHERE email = N'mai.nguyen@gonnha.vn');
    SELECT @NamStaffId = staff_id FROM dbo.Staff WHERE user_id = (SELECT user_id FROM dbo.Users WHERE email = N'nam.tran@gonnha.vn');

    IF @MaiStaffId IS NOT NULL AND NOT EXISTS (SELECT 1 FROM dbo.Staff_Follows WHERE customer_id = @CustId AND staff_id = @MaiStaffId)
        INSERT INTO dbo.Staff_Follows (customer_id, staff_id, created_at) VALUES (@CustId, @MaiStaffId, SYSDATETIME());

    IF @NamStaffId IS NOT NULL AND NOT EXISTS (SELECT 1 FROM dbo.Staff_Follows WHERE customer_id = @CustId AND staff_id = @NamStaffId)
        INSERT INTO dbo.Staff_Follows (customer_id, staff_id, created_at) VALUES (@CustId, @NamStaffId, SYSDATETIME());
END;

-- 8. SEED LỊCH SẴN SÀNG CHO TẤT CẢ NHÂN VIÊN (30 ngày tới)
DECLARE @DStart DATE = CAST(SYSDATETIME() AS DATE);
DECLARE @DEnd DATE = DATEADD(day, 35, @DStart);

;WITH DateRange AS (
    SELECT @DStart AS [Date]
    UNION ALL
    SELECT DATEADD(day, 1, [Date])
    FROM DateRange
    WHERE [Date] < @DEnd
)
INSERT INTO dbo.Staff_Availability (staff_id, work_date, start_time, end_time, created_at, is_active)
SELECT s.staff_id, d.[Date], CAST('08:00:00' AS TIME), CAST('18:00:00' AS TIME), SYSDATETIME(), 1
FROM dbo.Staff s
CROSS JOIN DateRange d
WHERE NOT EXISTS (
    SELECT 1 FROM dbo.Staff_Availability a
    WHERE a.staff_id = s.staff_id AND a.work_date = d.[Date]
)
OPTION (MAXRECURSION 100);

-- 9. SEED VÍ NỘI BỘ (UserWallets) CHO TOÀN BỘ USERS NẾU CHƯA CÓ
IF EXISTS (SELECT * FROM sys.tables WHERE name = 'UserWallets')
BEGIN
    INSERT INTO dbo.UserWallets (user_id, balance, updated_at)
    SELECT u.user_id, 0.00, SYSUTCDATETIME()
    FROM dbo.Users u
    WHERE NOT EXISTS (SELECT 1 FROM dbo.UserWallets w WHERE w.user_id = u.user_id);
END;

COMMIT TRANSACTION;
PRINT N'Đã hoàn tất nạp dữ liệu mẫu và đồng bộ hệ thống thành công!';
