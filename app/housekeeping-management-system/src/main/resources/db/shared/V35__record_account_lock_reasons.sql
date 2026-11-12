SET XACT_ABORT ON;

-- Lý do khóa tài khoản và người/thời điểm thay đổi trạng thái gần nhất.
-- Tài khoản đã khóa trước migration này giữ lý do NULL; mọi lần khóa mới đều phải có lý do.
ALTER TABLE dbo.Users ADD
    status_reason nvarchar(500) NULL,
    status_changed_at datetime2(0) NULL,
    status_changed_by bigint NULL,
    CONSTRAINT CK_Users_StatusReasonOnlyWhenLocked
        CHECK (status_reason IS NULL OR (status = 'INACTIVE' AND LEN(LTRIM(RTRIM(status_reason))) > 0)),
    CONSTRAINT CK_Users_StatusChangeAudit
        CHECK ((status_changed_at IS NULL AND status_changed_by IS NULL)
            OR (status_changed_at IS NOT NULL AND status_changed_by IS NOT NULL)),
    CONSTRAINT FK_Users_StatusChangedBy_Users
        FOREIGN KEY (status_changed_by) REFERENCES dbo.Users(user_id);
