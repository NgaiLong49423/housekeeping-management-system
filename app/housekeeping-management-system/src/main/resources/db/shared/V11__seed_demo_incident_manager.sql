SET XACT_ABORT ON;

-- Give the isolated demo database a second Admin so financial and staff actions
-- can be proposed and approved by different accounts. Production databases are unchanged.
IF UPPER(DB_NAME()) = N'HOME_CLEANING_DEMO'
BEGIN
    IF EXISTS (
        SELECT 1
        FROM dbo.Users
        WHERE email = N'manager@demo.gonnha.local'
          AND (role <> 'ADMIN' OR status <> 'ACTIVE')
    )
        THROW 51120, 'The demo manager email already belongs to an inactive or non-Admin account.', 1;

    INSERT INTO dbo.Users (full_name, email, password_hash, phone, role, status)
    SELECT N'Quản lý Demo', N'manager@demo.gonnha.local',
           N'{bcrypt}$2a$10$J9pdp//ZJo3yTljIx9WD6O.Zt.4WhoDHld3p/uzDzGWDiBKe0Da.i',
           N'0900000005', 'ADMIN', 'ACTIVE'
    WHERE NOT EXISTS (
        SELECT 1 FROM dbo.Users WHERE email = N'manager@demo.gonnha.local'
    );

    UPDATE dbo.Users
    SET can_manage_incidents = 1
    WHERE email = N'manager@demo.gonnha.local'
      AND role = 'ADMIN'
      AND status = 'ACTIVE';

    IF @@ROWCOUNT <> 1
        THROW 51121, 'Could not initialize the active demo incident manager.', 1;
END;
