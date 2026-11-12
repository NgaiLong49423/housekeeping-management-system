SET XACT_ABORT ON;

ALTER TABLE dbo.Users DROP CONSTRAINT CK_Users_Role;
ALTER TABLE dbo.Users ADD CONSTRAINT CK_Users_Role
    CHECK (role IN ('CUSTOMER', 'STAFF', 'ADMIN', 'MANAGER'));

DECLARE @manager_user_id BIGINT;
SELECT TOP (1) @manager_user_id = user_id
FROM dbo.Users
WHERE role = 'ADMIN' AND can_manage_incidents = 1
ORDER BY CASE WHEN status = 'ACTIVE' THEN 0 ELSE 1 END,
         CASE WHEN email = N'manager@demo.gonnha.local' THEN 0 ELSE 1 END, user_id;

IF @manager_user_id IS NOT NULL
    UPDATE dbo.Users SET role = 'MANAGER', can_manage_incidents = 0 WHERE user_id = @manager_user_id;

ALTER TABLE dbo.Users DROP CONSTRAINT CK_Users_CanManageIncidentsRole;
ALTER TABLE dbo.Users DROP CONSTRAINT DF_Users_CanManageIncidents;
ALTER TABLE dbo.Users DROP COLUMN can_manage_incidents;

CREATE UNIQUE INDEX UX_Users_Single_Manager ON dbo.Users(role) WHERE role = 'MANAGER';
