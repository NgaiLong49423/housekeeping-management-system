-- Flyway callback: chạy tự động trước bước kiểm tra lịch sử migration, mỗi lần ứng dụng khởi động.
--
-- Một số migration đã chạy trên database của thành viên (và trong database/Housekeeping_Database.bak)
-- sau đó được đổi số phiên bản để tránh trùng số. Nội dung các file không đổi, chỉ tên/số phiên bản đổi,
-- nên ở đây chỉ cần ghi lại số phiên bản mới cho đúng các dòng lịch sử cũ. Không chạy lại migration nào.
--   V32__rename_demo_accounts_to_realistic_names      -> V35
--   V34__add_cancellation_penalty_policy_and_wallets  -> V32
--   V34__record_account_lock_reasons (bản đầu PR #8)  -> V37
--   V35__record_account_lock_reasons                  -> V37
--   V36__repair_truncated_staff_password_hashes       -> V42
--   V42__support_multi_staff_bookings                  -> V43 (checksum 1917262565)
-- Database mới hoặc đã đúng số thì không có dòng nào bị đổi.
IF OBJECT_ID(N'[${flyway:defaultSchema}].[${flyway:table}]', N'U') IS NOT NULL
BEGIN
    DECLARE @history NVARCHAR(300) = QUOTENAME(N'${flyway:defaultSchema}') + N'.' + QUOTENAME(N'${flyway:table}');
    DECLARE @renumber NVARCHAR(MAX) = N'
        DECLARE @renames TABLE (old_script NVARCHAR(1000) NOT NULL, new_version NVARCHAR(50) NOT NULL,
                                new_script NVARCHAR(1000) NOT NULL);
        INSERT INTO @renames (old_script, new_version, new_script) VALUES
            (N''V32__rename_demo_accounts_to_realistic_names.sql'', N''35'', N''V35__rename_demo_accounts_to_realistic_names.sql''),
            (N''V34__add_cancellation_penalty_policy_and_wallets.sql'', N''32'', N''V32__add_cancellation_penalty_policy_and_wallets.sql''),
            (N''V34__record_account_lock_reasons.sql'', N''37'', N''V37__record_account_lock_reasons.sql''),
            (N''V35__record_account_lock_reasons.sql'', N''37'', N''V37__record_account_lock_reasons.sql''),
            (N''V36__repair_truncated_staff_password_hashes.sql'', N''42'', N''V42__repair_truncated_staff_password_hashes.sql''),
            (N''V42__support_multi_staff_bookings.sql'', N''43'', N''V43__support_multi_staff_bookings.sql'');

        -- Đổi đồng thời trong một câu lệnh để các số cũ/mới không đè lên nhau.
        UPDATE history
        SET version = renames.new_version, script = renames.new_script
        FROM ' + @history + N' history
        JOIN @renames renames ON renames.old_script = history.script
        WHERE history.success = 1
          AND (renames.old_script <> N''V42__support_multi_staff_bookings.sql''
               OR (history.version = N''42'' AND history.checksum = 1917262565))
          AND NOT EXISTS (SELECT 1 FROM ' + @history + N' existing WHERE existing.script = renames.new_script);';
    EXEC sys.sp_executesql @renumber;
END;
