-- Sample staff and availability data for Housekeeping_Database
-- Safe, idempotent execution using IF NOT EXISTS
SET XACT_ABORT ON;
BEGIN TRANSACTION;

-- 1. Bổ sung lịch làm việc cho 2 nhân viên hiện có chưa có lịch (Staff 5 & Staff 7)
DECLARE @DateStart DATE = '2026-11-12';
DECLARE @DateEnd DATE = '2026-12-15';

;WITH DateRange AS (
    SELECT @DateStart AS [Date]
    UNION ALL
    SELECT DATEADD(day, 1, [Date])
    FROM DateRange
    WHERE [Date] < @DateEnd
)
INSERT INTO dbo.Staff_Availability (staff_id, work_date, start_time, end_time, created_at, is_active)
SELECT s.staff_id, d.[Date], CAST('08:00:00' AS TIME), CAST('18:00:00' AS TIME), SYSDATETIME(), 1
FROM dbo.Staff s
CROSS JOIN DateRange d
WHERE s.staff_id IN (5, 7)
  AND NOT EXISTS (
      SELECT 1 FROM dbo.Staff_Availability a
      WHERE a.staff_id = s.staff_id AND a.work_date = d.[Date]
  )
OPTION (MAXRECURSION 100);

-- 2. Thêm 6 nhân viên mới
DECLARE @PassHash NVARCHAR(255) = N'{bcrypt}$2a$10$Y85jiA/BKR2TZlb/1kHPWeL79WzLTeWZgv4jiNZHsAWWDjl/qWFoO';

DECLARE @NewStaff TABLE (
    full_name NVARCHAR(150),
    email NVARCHAR(255) COLLATE Latin1_General_100_CI_AS,
    phone NVARCHAR(20),
    staff_type VARCHAR(20),
    avatar_url NVARCHAR(500),
    bio NVARCHAR(MAX)
);

INSERT INTO @NewStaff (full_name, email, phone, staff_type, avatar_url, bio)
VALUES
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
 N'https://images.unsplash.com/photo-1492562080023-ab3db95bfbce?w=200', 
 N'Trách nhiệm cao, tác phong nhanh nhẹn, chuyên giặt hấp sofa, thảm trải sàn và nệm cao su.');

-- Insert vào dbo.Users
INSERT INTO dbo.Users (full_name, email, password_hash, phone, role, status)
SELECT s.full_name, s.email, @PassHash, s.phone, 'STAFF', 'ACTIVE'
FROM @NewStaff s
WHERE NOT EXISTS (SELECT 1 FROM dbo.Users u WHERE u.email = s.email);

-- Insert vào dbo.Staff
INSERT INTO dbo.Staff (user_id, staff_type)
SELECT u.user_id, s.staff_type
FROM @NewStaff s
JOIN dbo.Users u ON u.email = s.email
WHERE NOT EXISTS (SELECT 1 FROM dbo.Staff st WHERE st.user_id = u.user_id);

-- Insert vào dbo.Staff_Profiles
INSERT INTO dbo.Staff_Profiles (staff_id, avatar_url, bio)
SELECT st.staff_id, s.avatar_url, s.bio
FROM @NewStaff s
JOIN dbo.Users u ON u.email = s.email
JOIN dbo.Staff st ON st.user_id = u.user_id
WHERE NOT EXISTS (SELECT 1 FROM dbo.Staff_Profiles p WHERE p.staff_id = st.staff_id);

-- Tạo lịch làm việc cho các nhân viên mới từ hôm nay đến 15/12/2026
;WITH DateRange AS (
    SELECT @DateStart AS [Date]
    UNION ALL
    SELECT DATEADD(day, 1, [Date])
    FROM DateRange
    WHERE [Date] < @DateEnd
)
INSERT INTO dbo.Staff_Availability (staff_id, work_date, start_time, end_time, created_at, is_active)
SELECT st.staff_id, d.[Date], CAST('08:00:00' AS TIME), CAST('18:00:00' AS TIME), SYSDATETIME(), 1
FROM @NewStaff s
JOIN dbo.Users u ON u.email = s.email
JOIN dbo.Staff st ON st.user_id = u.user_id
CROSS JOIN DateRange d
WHERE NOT EXISTS (
    SELECT 1 FROM dbo.Staff_Availability a
    WHERE a.staff_id = st.staff_id AND a.work_date = d.[Date]
)
OPTION (MAXRECURSION 100);

-- 3. Bổ sung các gói dịch vụ dọn dẹp chuyên nghiệp
DECLARE @NewServices TABLE (
    service_name NVARCHAR(150),
    description NVARCHAR(MAX),
    price DECIMAL(12,2),
    duration_minutes INT,
    status VARCHAR(20),
    image_url NVARCHAR(500)
);

INSERT INTO @NewServices (service_name, description, price, duration_minutes, status, image_url)
VALUES
(N'Vệ sinh toilet & phòng tắm chuyên sâu', 
 N'Tẩy ố vàng gạch men, khử cặn canxi trên vách kính tắm và vòi sen inox, khử khuẩn bồn cầu, khử mùi hôi cống thoát nước.', 
 130000.00, 90, 'ACTIVE', 
 N'https://images.unsplash.com/photo-1584622650111-993a426fbf0a?w=600'),

(N'Giặt hấp sofa & đệm ngủ tại nhà', 
 N'Phun hút hơi nước nóng 100°C diệt 99% vi khuẩn và nấm mốc, hút sạch bụi mịn và mạt bụi trên sofa vải nỉ, da, đệm lò xo.', 
 280000.00, 150, 'ACTIVE', 
 N'https://images.unsplash.com/photo-1555041469-a586c61ea9bc?w=600'),

(N'Vệ sinh máy lạnh & lưới lọc bụi', 
 N'Xịt rửa dàn lạnh, vệ sinh lưới lọc kháng khuẩn, thông ống thoát nước ngưng và kiểm tra độ lạnh giúp tiết kiệm điện.', 
 160000.00, 60, 'ACTIVE', 
 N'https://images.unsplash.com/photo-1621905251189-08b45d6a269e?w=600'),

(N'Ủi đồ & Chăm sóc trang phục', 
 N'Ủi phẳng quần áo sơ mi, váy đầm, vest bằng bàn ủi hơi nước đứng cao cấp, gấp gọn gàng hoặc treo tủ theo yêu cầu.', 
 100000.00, 60, 'ACTIVE', 
 N'https://images.unsplash.com/photo-1517677208171-0bc6725a3e60?w=600'),

(N'Khử khuẩn & Khử mùi sinh học Nano', 
 N'Phun sương Nano bạc khử trùng toàn bộ bề mặt nội thất, tiêu diệt vi khuẩn, khử mùi ẩm mốc, khói thuốc và thú cưng.', 
 200000.00, 90, 'ACTIVE', 
 N'https://images.unsplash.com/photo-1585421514738-01798e348b17?w=600'),

(N'Dọn dẹp & Chăm sóc ban công sân vườn', 
 N'Quét dọn lá cây, chà rong rêu bám trên gạch ban công, tưới cây cảnh, sắp xếp bàn ghế ngoài trời ngăn nắp và sạch thoáng.', 
 140000.00, 90, 'ACTIVE', 
 N'https://images.unsplash.com/photo-1598928506311-c55ded91a20c?w=600');

INSERT INTO dbo.Services (service_name, description, price, duration_minutes, status, image_url)
SELECT s.service_name, s.description, s.price, s.duration_minutes, s.status, s.image_url
FROM @NewServices s
WHERE NOT EXISTS (SELECT 1 FROM dbo.Services existing WHERE existing.service_name = s.service_name);

-- 4. Tạo tài khoản Quản lý cấp cao (Manager) nếu chưa có
IF NOT EXISTS (SELECT 1 FROM dbo.Users WHERE role = 'MANAGER')
BEGIN
    INSERT INTO dbo.Users (full_name, email, password_hash, phone, role, status)
    VALUES (N'Quản lý cấp cao', N'manager@gonnha.vn',
            @PassHash, N'0900000005', 'MANAGER', 'ACTIVE');
END;

COMMIT TRANSACTION;
