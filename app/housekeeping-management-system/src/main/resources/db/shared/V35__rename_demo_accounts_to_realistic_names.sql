SET XACT_ABORT ON;

-- Replace placeholder "... Demo" names of the seeded demo accounts with realistic Vietnamese names.
-- Only the isolated demo database is touched, and only while the placeholder name is still in place,
-- so names edited later through the application are preserved. Bookings read the Staff/Customer
-- name from dbo.Users, therefore existing bookings show the new names without further changes.
IF UPPER(DB_NAME()) = N'HOME_CLEANING_DEMO'
BEGIN
    DECLARE @renames TABLE (
        email NVARCHAR(255) COLLATE Latin1_General_100_CI_AS NOT NULL PRIMARY KEY,
        old_name NVARCHAR(150) NOT NULL,
        new_name NVARCHAR(150) NOT NULL
    );

    INSERT INTO @renames (email, old_name, new_name)
    VALUES
        (N'customer@demo.gonnha.local', N'Khách hàng Demo', N'Trần Thị Thu Hà'),
        (N'staff@demo.gonnha.local', N'Nhân viên bán thời gian Demo', N'Lê Thị Hồng Nhung'),
        (N'staff.fulltime@demo.gonnha.local', N'Nhân viên toàn thời gian Demo', N'Phạm Văn Hùng'),
        (N'admin@demo.gonnha.local', N'Quản trị viên Demo', N'Võ Thanh Tùng'),
        (N'manager@demo.gonnha.local', N'Quản lý Demo', N'Đặng Quốc Bảo');

    -- Keep the recipient on the demo customer's address in sync when it still carries the placeholder.
    UPDATE house
    SET recipient_name = renamed.new_name
    FROM dbo.Houses house
    JOIN dbo.Users account ON account.user_id = house.customer_id
    JOIN @renames renamed ON renamed.email = account.email
    WHERE house.recipient_name = renamed.old_name;

    UPDATE account
    SET full_name = renamed.new_name
    FROM dbo.Users account
    JOIN @renames renamed ON renamed.email = account.email
    WHERE account.full_name = renamed.old_name;
END;
