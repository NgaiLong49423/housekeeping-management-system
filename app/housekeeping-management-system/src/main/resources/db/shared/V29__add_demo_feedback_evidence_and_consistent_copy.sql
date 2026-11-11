SET XACT_ABORT ON;

IF UPPER(DB_NAME()) = N'HOME_CLEANING_DEMO'
BEGIN
    DECLARE @DemoCustomerId BIGINT = (
        SELECT user_id FROM dbo.Users WHERE email=N'customer@demo.gonnha.local' AND role='CUSTOMER'
    );
    DECLARE @DemoAdminId BIGINT = (
        SELECT user_id FROM dbo.Users WHERE email=N'admin@demo.gonnha.local' AND role='ADMIN'
    );
    DECLARE @CleanText NVARCHAR(1000) = N'Sau buổi dọn, khu vực bếp vẫn còn bụi và vết nước trên mặt bàn. Mong công ty kiểm tra giúp. (Ảnh minh họa - dữ liệu demo.)';
    DECLARE @DamageText NVARCHAR(1000) = N'Bình gốm trang trí bị nứt sau buổi dọn. Tôi gửi ảnh minh họa để công ty kiểm tra; giá trị ước tính 280.000 ₫ chỉ để tham khảo. (Dữ liệu demo.)';
    DECLARE @CleanReply NVARCHAR(MAX) = N'Công ty đã tiếp nhận phản ánh và đang kiểm tra ảnh minh họa demo. Chúng tôi sẽ trao đổi cách khắc phục sau khi xác minh.';
    DECLARE @DamageReply NVARCHAR(MAX) = N'Công ty đang xác minh phản ánh bằng ảnh minh họa. Đề xuất 120.000 ₫ chỉ là dữ liệu mẫu để bạn xem xét; chưa được bạn đồng ý, Quản lý phê duyệt hoặc công ty chi trả.';
    DECLARE @CleanFeedbackId BIGINT;
    DECLARE @DamageFeedbackId BIGINT;
    DECLARE @CleanCaseId BIGINT;
    DECLARE @DamageCaseId BIGINT;
    DECLARE @CleanMessageId BIGINT;
    DECLARE @DamageMessageId BIGINT;
    DECLARE @DamageEvidenceId BIGINT;

    IF @DemoCustomerId IS NOT NULL AND @DemoAdminId IS NOT NULL
    BEGIN
        SELECT TOP (1) @CleanFeedbackId=f.feedback_id, @CleanCaseId=c.case_id
        FROM dbo.Feedbacks f
        JOIN dbo.Feedback_Cases c ON c.feedback_id=f.feedback_id
        WHERE f.customer_id=@DemoCustomerId AND c.incident_types=N'CLEANLINESS'
          AND (f.content IN (
              N'Một số khu vực vẫn còn bụi sau khi dọn. Tôi muốn công ty kiểm tra và đề xuất cách khắc phục.',
              N'Sau buổi dọn, khu vực bếp vẫn còn bụi và vết nước trên mặt bàn. Mong công ty kiểm tra giúp. (Ảnh minh họa - dữ liệu demo.)'
          ) OR c.customer_description IN (
              N'Một số khu vực vẫn còn bụi sau khi dọn. Tôi muốn công ty kiểm tra và đề xuất cách khắc phục.',
              N'Sau buổi dọn, khu vực bếp vẫn còn bụi và vết nước trên mặt bàn. Mong công ty kiểm tra giúp. (Ảnh minh họa - dữ liệu demo.)'
          ))
        ORDER BY f.feedback_id;

        SELECT TOP (1) @DamageFeedbackId=f.feedback_id, @DamageCaseId=c.case_id
        FROM dbo.Feedbacks f
        JOIN dbo.Feedback_Cases c ON c.feedback_id=f.feedback_id
        WHERE f.customer_id=@DemoCustomerId AND c.incident_types=N'PROPERTY_DAMAGE'
          AND (f.content IN (
              N'Món đồ trang trí bị nứt trong lúc làm dịch vụ. Đây là thông tin ban đầu để công ty xác minh.',
              N'Bình gốm trang trí bị nứt sau buổi dọn. Tôi gửi ảnh minh họa để công ty kiểm tra; giá trị ước tính 280.000 ₫ chỉ để tham khảo. (Dữ liệu demo.)'
          ) OR c.customer_description IN (
              N'Món đồ trang trí bị nứt trong lúc làm dịch vụ. Đây là thông tin ban đầu để công ty xác minh.',
              N'Bình gốm trang trí bị nứt sau buổi dọn. Tôi gửi ảnh minh họa để công ty kiểm tra; giá trị ước tính 280.000 ₫ chỉ để tham khảo. (Dữ liệu demo.)'
          ))
        ORDER BY f.feedback_id;

        IF @CleanCaseId IS NOT NULL
        BEGIN
            UPDATE dbo.Feedbacks SET content=@CleanText, status='RESPONDED', admin_reply=@CleanReply
            WHERE feedback_id=@CleanFeedbackId;
            UPDATE dbo.Feedback_Cases
            SET customer_description=@CleanText,
                customer_requested_resolution=N'Mong công ty kiểm tra khu vực bếp và trao đổi cách khắc phục.',
                case_status='INVESTIGATING',
                internal_finding=N'Đang kiểm tra phản ánh vệ sinh và ảnh minh họa demo; chưa chốt cách khắc phục.'
            WHERE case_id=@CleanCaseId AND case_status<>'RESOLVED';
            UPDATE dbo.Feedback_Case_Messages
            SET message_text=@CleanText
            WHERE case_id=@CleanCaseId AND sender_role='CUSTOMER'
              AND (legacy_imported=1 OR message_id=(SELECT MIN(message_id) FROM dbo.Feedback_Case_Messages WHERE case_id=@CleanCaseId AND sender_role='CUSTOMER'));
            UPDATE dbo.Feedback_Case_Messages SET message_text=@CleanReply
            WHERE case_id=@CleanCaseId AND sender_role='ADMIN';

            SELECT TOP (1) @CleanMessageId=message_id
            FROM dbo.Feedback_Case_Messages
            WHERE case_id=@CleanCaseId AND sender_role='CUSTOMER'
            ORDER BY message_id;

            IF NOT EXISTS (SELECT 1 FROM dbo.Feedback_Case_Evidence WHERE case_id=@CleanCaseId AND image_path=N'demo-feedback-cleanliness.png')
                INSERT INTO dbo.Feedback_Case_Evidence(case_id,image_path,uploaded_by,message_id,media_type,content_type,original_name,external_url,uploaded_at)
                VALUES (@CleanCaseId,N'demo-feedback-cleanliness.png',@DemoCustomerId,@CleanMessageId,'IMAGE','image/png',N'Ảnh minh họa - khu vực bếp (demo).png',NULL,SYSDATETIME());

            UPDATE verification SET case_status='INVESTIGATING', reviewed_evidence_ids=N'',
                internal_finding=N'Đang kiểm tra ảnh minh họa demo về khu vực bếp; ảnh không phải bằng chứng của khách thật.'
            FROM dbo.Feedback_Case_Verifications verification
            WHERE verification.verification_id=(SELECT MAX(v.verification_id) FROM dbo.Feedback_Case_Verifications v WHERE v.case_id=@CleanCaseId);
        END;

        IF @DamageCaseId IS NOT NULL
        BEGIN
            UPDATE dbo.Feedbacks SET content=@DamageText, status='RESPONDED', admin_reply=@DamageReply
            WHERE feedback_id=@DamageFeedbackId;
            UPDATE dbo.Feedback_Cases
            SET customer_description=@DamageText,
                customer_requested_resolution=N'Mong công ty xác minh tình trạng món đồ và trao đổi phương án phù hợp.',
                case_status='WAITING_CUSTOMER',
                internal_finding=N'Ảnh minh họa mô phỏng vết nứt; chưa có hóa đơn chứng minh giá mua. Đề xuất 120.000 ₫ là dữ liệu mẫu, chưa được khách hàng đồng ý, Quản lý phê duyệt hoặc công ty chi trả.'
            WHERE case_id=@DamageCaseId AND case_status<>'RESOLVED';
            UPDATE dbo.Feedback_Case_Messages
            SET message_text=@DamageText
            WHERE case_id=@DamageCaseId AND sender_role='CUSTOMER'
              AND (legacy_imported=1 OR message_id=(SELECT MIN(message_id) FROM dbo.Feedback_Case_Messages WHERE case_id=@DamageCaseId AND sender_role='CUSTOMER'));
            UPDATE dbo.Feedback_Case_Messages SET message_text=@DamageReply
            WHERE case_id=@DamageCaseId AND sender_role='ADMIN';

            SELECT TOP (1) @DamageMessageId=message_id
            FROM dbo.Feedback_Case_Messages
            WHERE case_id=@DamageCaseId AND sender_role='CUSTOMER'
            ORDER BY message_id;

            IF NOT EXISTS (SELECT 1 FROM dbo.Feedback_Case_Evidence WHERE case_id=@DamageCaseId AND image_path=N'demo-feedback-damaged-vase.png')
                INSERT INTO dbo.Feedback_Case_Evidence(case_id,image_path,uploaded_by,message_id,media_type,content_type,original_name,external_url,uploaded_at)
                VALUES (@DamageCaseId,N'demo-feedback-damaged-vase.png',@DemoCustomerId,@DamageMessageId,'IMAGE','image/png',N'Ảnh minh họa - bình gốm nứt (demo).png',NULL,SYSDATETIME());

            SELECT @DamageEvidenceId=evidence_id FROM dbo.Feedback_Case_Evidence WHERE case_id=@DamageCaseId AND image_path=N'demo-feedback-damaged-vase.png';
            UPDATE verification SET case_status='VERIFIED', reviewed_evidence_ids=CONVERT(NVARCHAR(20),@DamageEvidenceId),
                internal_finding=N'Ảnh minh họa mô phỏng vết nứt đã được xem; không đại diện hiện trạng thật và không có hóa đơn chứng minh giá mua. Khoản 120.000 ₫ chỉ là đề xuất mẫu, chưa được khách hàng đồng ý, Quản lý phê duyệt hoặc công ty chi trả.'
            FROM dbo.Feedback_Case_Verifications verification
            WHERE verification.verification_id=(SELECT MAX(v.verification_id) FROM dbo.Feedback_Case_Verifications v WHERE v.case_id=@DamageCaseId);

            UPDATE dbo.Booking_Financial_Entries
            SET reason=N'Đề xuất minh họa 120.000 ₫ để khách hàng xem xét. Ảnh minh họa mô phỏng vết nứt; chưa có hóa đơn chứng minh giá mua. Chưa được khách hàng đồng ý, Quản lý phê duyệt hoặc công ty chi trả.'
            WHERE case_id=@DamageCaseId AND entry_type='COMPENSATION'
              AND status IN ('PROPOSED','PENDING_APPROVAL');
        END;
    END;
END;
