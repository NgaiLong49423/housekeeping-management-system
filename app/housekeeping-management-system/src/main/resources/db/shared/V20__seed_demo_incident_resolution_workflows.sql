-- Seed two end-to-end incident examples only in the isolated SQL Server demo database.
SET XACT_ABORT ON;

IF UPPER(DB_NAME()) = N'HOME_CLEANING_DEMO'
BEGIN
    DECLARE @CustomerId BIGINT = (SELECT user_id FROM dbo.Users WHERE email=N'customer@demo.gonnha.local' AND role='CUSTOMER');
    DECLARE @AdminId BIGINT = (SELECT user_id FROM dbo.Users WHERE email=N'admin@demo.gonnha.local' AND role='ADMIN');
    DECLARE @ManagerId BIGINT = (SELECT user_id FROM dbo.Users WHERE email=N'manager@demo.gonnha.local' AND role='MANAGER');
    DECLARE @PartTimeStaffId BIGINT = (
        SELECT staff.staff_id FROM dbo.Staff staff
        JOIN dbo.Users account ON account.user_id=staff.user_id
        WHERE account.email=N'staff@demo.gonnha.local' AND account.role='STAFF'
    );
    DECLARE @FullTimeStaffId BIGINT = (
        SELECT staff.staff_id FROM dbo.Staff staff
        JOIN dbo.Users account ON account.user_id=staff.user_id
        WHERE account.email=N'staff.fulltime@demo.gonnha.local' AND account.role='STAFF'
    );
    DECLARE @HouseId BIGINT = (
        SELECT TOP (1) house_id FROM dbo.Houses
        WHERE customer_id=@CustomerId AND is_deleted=0 ORDER BY is_default DESC, house_id
    );
    DECLARE @ServiceId BIGINT;
    DECLARE @ServiceName NVARCHAR(150);
    DECLARE @Price DECIMAL(12,2);
    DECLARE @Duration INT;
    DECLARE @Now DATETIME2(0) = CAST(SYSDATETIMEOFFSET() AT TIME ZONE 'SE Asia Standard Time' AS DATETIME2(0));
    DECLARE @CleanStart DATETIME2(0) = DATEADD(HOUR, 10, CAST(DATEADD(DAY, -2, CONVERT(DATE, @Now)) AS DATETIME2(0)));
    DECLARE @DamageStart DATETIME2(0) = DATEADD(HOUR, 15, CAST(DATEADD(DAY, -4, CONVERT(DATE, @Now)) AS DATETIME2(0)));
    DECLARE @CleanBookingId BIGINT;
    DECLARE @DamageBookingId BIGINT;
    DECLARE @CleanFeedbackId BIGINT;
    DECLARE @DamageFeedbackId BIGINT;
    DECLARE @CleanCaseId BIGINT;
    DECLARE @DamageCaseId BIGINT;
    DECLARE @CleanPlanId BIGINT;
    DECLARE @DamagePlanId BIGINT;
    DECLARE @CleanOptionId BIGINT;
    DECLARE @DamageOptionId BIGINT;

    IF @CustomerId IS NULL OR @AdminId IS NULL OR @ManagerId IS NULL
       OR @PartTimeStaffId IS NULL OR @FullTimeStaffId IS NULL OR @HouseId IS NULL
        THROW 51130, 'Required demo customer, admin, manager, staff, or house is missing.', 1;

    SELECT TOP (1) @ServiceId=service_id, @ServiceName=service_name,
        @Price=price, @Duration=duration_minutes
    FROM dbo.Services WHERE service_name=N'Dọn dẹp nhà cơ bản' AND status='ACTIVE'
    ORDER BY service_id;

    IF @ServiceId IS NULL
        THROW 51131, 'The active demo cleaning service is missing.', 1;

    SELECT @CleanBookingId=booking_id FROM dbo.Bookings
    WHERE customer_id=@CustomerId AND staff_id=@PartTimeStaffId AND start_time=@CleanStart;
    IF @CleanBookingId IS NULL
    BEGIN
        INSERT INTO dbo.Bookings (
            customer_id, staff_id, original_staff_id, house_id, address_snapshot,
            latitude_snapshot, longitude_snapshot, address_note_snapshot,
            start_time, end_time, total_duration_minutes, total_amount, status
        )
        SELECT @CustomerId, @PartTimeStaffId, @PartTimeStaffId, house.house_id, house.address,
               house.latitude, house.longitude, house.address_note,
               @CleanStart, DATEADD(MINUTE, @Duration, @CleanStart), @Duration, @Price, 'COMPLETED'
        FROM dbo.Houses house WHERE house.house_id=@HouseId;
        SET @CleanBookingId = SCOPE_IDENTITY();
    END;

    SELECT @DamageBookingId=booking_id FROM dbo.Bookings
    WHERE customer_id=@CustomerId AND staff_id=@FullTimeStaffId AND start_time=@DamageStart;
    IF @DamageBookingId IS NULL
    BEGIN
        INSERT INTO dbo.Bookings (
            customer_id, staff_id, original_staff_id, house_id, address_snapshot,
            latitude_snapshot, longitude_snapshot, address_note_snapshot,
            start_time, end_time, total_duration_minutes, total_amount, status
        )
        SELECT @CustomerId, @FullTimeStaffId, @FullTimeStaffId, house.house_id, house.address,
               house.latitude, house.longitude, house.address_note,
               @DamageStart, DATEADD(MINUTE, @Duration, @DamageStart), @Duration, @Price, 'COMPLETED'
        FROM dbo.Houses house WHERE house.house_id=@HouseId;
        SET @DamageBookingId = SCOPE_IDENTITY();
    END;

    INSERT INTO dbo.Booking_Details (booking_id, service_id, service_name_snapshot, unit_price, duration_minutes)
    SELECT fixture.booking_id, @ServiceId, @ServiceName, @Price, @Duration
    FROM (VALUES (@CleanBookingId), (@DamageBookingId)) fixture(booking_id)
    WHERE NOT EXISTS (SELECT 1 FROM dbo.Booking_Details detail WHERE detail.booking_id=fixture.booking_id);

    INSERT INTO dbo.Payments (booking_id, amount, payment_method, status, created_at, paid_at)
    SELECT fixture.booking_id, @Price, 'CASH', 'SUCCESS', @Now, DATEADD(MINUTE, @Duration, fixture.start_time)
    FROM (VALUES (@CleanBookingId, @CleanStart), (@DamageBookingId, @DamageStart)) fixture(booking_id, start_time)
    WHERE NOT EXISTS (SELECT 1 FROM dbo.Payments payment WHERE payment.booking_id=fixture.booking_id AND payment.status='SUCCESS');

    INSERT INTO dbo.Feedbacks (booking_id, customer_id, content, rating, topics, status)
    SELECT @CleanBookingId, @CustomerId,
           N'Một số khu vực vẫn còn bụi sau khi dọn. Tôi muốn công ty kiểm tra và đề xuất cách khắc phục.',
           2, N'CLEANLINESS', 'OPEN'
    WHERE NOT EXISTS (SELECT 1 FROM dbo.Feedbacks WHERE booking_id=@CleanBookingId);
    INSERT INTO dbo.Feedbacks (booking_id, customer_id, content, rating, topics, status)
    SELECT @DamageBookingId, @CustomerId,
           N'Món đồ trang trí bị nứt trong lúc làm dịch vụ. Đây là thông tin ban đầu để công ty xác minh.',
           1, N'PROPERTY_DAMAGE', 'OPEN'
    WHERE NOT EXISTS (SELECT 1 FROM dbo.Feedbacks WHERE booking_id=@DamageBookingId);

    SELECT @CleanFeedbackId=feedback_id FROM dbo.Feedbacks WHERE booking_id=@CleanBookingId;
    SELECT @DamageFeedbackId=feedback_id FROM dbo.Feedbacks WHERE booking_id=@DamageBookingId;

    INSERT INTO dbo.Feedback_Cases (
        feedback_id, incident_types, customer_description, case_status, internal_finding,
        reviewed_by, reviewed_at, created_at, resolution_workflow_required
    )
    SELECT @CleanFeedbackId, N'CLEANLINESS',
           N'Một số khu vực vẫn còn bụi sau khi dọn. Tôi muốn công ty kiểm tra và đề xuất cách khắc phục.',
           'VERIFIED', N'Đã xác nhận phạm vi cần làm sạch lại; gửi khách lựa chọn giữa vệ sinh lại và hoàn một phần phí.',
           @AdminId, @Now, DATEADD(MINUTE, 10, @CleanStart), 1
    WHERE NOT EXISTS (SELECT 1 FROM dbo.Feedback_Cases WHERE feedback_id=@CleanFeedbackId);
    INSERT INTO dbo.Feedback_Cases (
        feedback_id, incident_types, customer_description, item_description, customer_estimated_value,
        item_age_months, case_status, internal_finding, reviewed_by, reviewed_at, created_at,
        resolution_workflow_required
    )
    SELECT @DamageFeedbackId, N'PROPERTY_DAMAGE',
           N'Món đồ trang trí bị nứt trong lúc làm dịch vụ. Đây là thông tin ban đầu để công ty xác minh.',
           N'Bình gốm trang trí', 280000, 18, 'VERIFIED',
           N'Đã trao đổi với khách, xem thông tin món đồ và thống nhất mức bồi thường 120.000 đồng để Manager duyệt.',
           @AdminId, @Now, DATEADD(MINUTE, 10, @DamageStart), 1
    WHERE NOT EXISTS (SELECT 1 FROM dbo.Feedback_Cases WHERE feedback_id=@DamageFeedbackId);

    SELECT @CleanCaseId=case_id FROM dbo.Feedback_Cases WHERE feedback_id=@CleanFeedbackId;
    SELECT @DamageCaseId=case_id FROM dbo.Feedback_Cases WHERE feedback_id=@DamageFeedbackId;

    INSERT INTO dbo.Feedback_Case_Verifications (case_id, admin_id, case_status, reviewed_evidence_ids, internal_finding, created_at)
    SELECT fixture.case_id, @AdminId, 'VERIFIED', N'[]', fixture.finding, @Now
    FROM (VALUES
        (@CleanCaseId, N'Đã xác nhận phạm vi cần làm sạch lại; gửi khách lựa chọn giữa vệ sinh lại và hoàn một phần phí.'),
        (@DamageCaseId, N'Đã trao đổi với khách, xem thông tin món đồ và thống nhất mức bồi thường 120.000 đồng để Manager duyệt.')
    ) fixture(case_id, finding)
    WHERE NOT EXISTS (SELECT 1 FROM dbo.Feedback_Case_Verifications verification WHERE verification.case_id=fixture.case_id);

    INSERT INTO dbo.Feedback_Case_Messages (case_id, sender_user_id, sender_role, message_text, sent_at)
    SELECT fixture.case_id, @CustomerId, 'CUSTOMER', fixture.customer_message, fixture.sent_at
    FROM (VALUES
        (@CleanCaseId, N'Một số khu vực vẫn còn bụi sau khi dọn. Tôi muốn công ty kiểm tra và đề xuất cách khắc phục.', DATEADD(MINUTE, 10, @CleanStart)),
        (@DamageCaseId, N'Món đồ trang trí bị nứt trong lúc làm dịch vụ. Đây là thông tin ban đầu để công ty xác minh.', DATEADD(MINUTE, 10, @DamageStart))
    ) fixture(case_id, customer_message, sent_at)
    WHERE NOT EXISTS (
        SELECT 1 FROM dbo.Feedback_Case_Messages message
        WHERE message.case_id=fixture.case_id AND message.sender_role='CUSTOMER' AND message.message_text=fixture.customer_message
    );

    INSERT INTO dbo.Feedback_Case_Messages (case_id, sender_user_id, sender_role, message_text, sent_at)
    SELECT fixture.case_id, @AdminId, 'ADMIN', fixture.admin_message, @Now
    FROM (VALUES
        (@CleanCaseId, N'Chúng tôi đã xác minh phản ánh. Vui lòng xem các phương án và chọn cách phù hợp với bạn.'),
        (@DamageCaseId, N'Công ty đã xác minh và thống nhất đề xuất bồi thường 120.000 đồng. Manager đang xem xét phương án.')
    ) fixture(case_id, admin_message)
    WHERE NOT EXISTS (
        SELECT 1 FROM dbo.Feedback_Case_Messages message
        WHERE message.case_id=fixture.case_id AND message.sender_role='ADMIN' AND message.message_text=fixture.admin_message
    );

    SELECT @CleanPlanId=plan_id FROM dbo.Feedback_Resolution_Plans WHERE case_id=@CleanCaseId AND version_no=1;
    IF @CleanPlanId IS NULL
    BEGIN
        INSERT INTO dbo.Feedback_Resolution_Plans (case_id, version_no, status, internal_finding, created_by, created_at)
        VALUES (@CleanCaseId, 1, 'AWAITING_CUSTOMER', N'Khách có thể chọn vệ sinh lại miễn phí hoặc nhận khoản hoàn một phần.', @AdminId, @Now);
        SET @CleanPlanId=SCOPE_IDENTITY();
    END;

    INSERT INTO dbo.Feedback_Resolution_Options (plan_id, option_no, title, description)
    SELECT @CleanPlanId, fixture.option_no, fixture.title, fixture.description
    FROM (VALUES
        (1, N'Vệ sinh lại miễn phí', N'Công ty sắp xếp nhân viên quay lại làm sạch các khu vực khách đã nêu.'),
        (2, N'Hoàn một phần phí dịch vụ', N'Hoàn 30.000 đồng cho phần dịch vụ chưa đạt yêu cầu.')
    ) fixture(option_no, title, description)
    WHERE NOT EXISTS (SELECT 1 FROM dbo.Feedback_Resolution_Options option_row WHERE option_row.plan_id=@CleanPlanId AND option_row.option_no=fixture.option_no);

    SELECT @CleanOptionId=option_id FROM dbo.Feedback_Resolution_Options WHERE plan_id=@CleanPlanId AND option_no=1;
    INSERT INTO dbo.Feedback_Resolution_Actions (option_id, action_type, description, due_at)
    SELECT @CleanOptionId, 'INTERNAL_WORK', N'Làm sạch lại các khu vực khách đã phản ánh, không thu thêm phí.', DATEADD(DAY, 2, @Now)
    WHERE NOT EXISTS (SELECT 1 FROM dbo.Feedback_Resolution_Actions WHERE option_id=@CleanOptionId);
    SELECT @CleanOptionId=option_id FROM dbo.Feedback_Resolution_Options WHERE plan_id=@CleanPlanId AND option_no=2;
    INSERT INTO dbo.Feedback_Resolution_Actions (option_id, action_type, description, amount, calculation_basis)
    SELECT @CleanOptionId, 'CUSTOMER_PAYOUT', N'Hoàn một phần phí dịch vụ.', 30000,
           N'Khoản hỗ trợ mẫu trong dữ liệu demo; chưa phải khoản đã duyệt hoặc đã chi.'
    WHERE NOT EXISTS (SELECT 1 FROM dbo.Feedback_Resolution_Actions WHERE option_id=@CleanOptionId);

    SELECT @DamagePlanId=plan_id FROM dbo.Feedback_Resolution_Plans WHERE case_id=@DamageCaseId AND version_no=1;
    IF @DamagePlanId IS NULL
    BEGIN
        INSERT INTO dbo.Feedback_Resolution_Plans (
            case_id, version_no, status, internal_finding, customer_response, selected_option_id,
            customer_payout_method, created_by, created_at, responded_at
        )
        VALUES (
            @DamageCaseId, 1, 'MANAGER_REVIEW',
            N'Khách và Admin đã thống nhất phương án bồi thường 120.000 đồng; chờ Manager duyệt.',
            N'Tôi đồng ý phương án bồi thường 120.000 đồng và nhận bằng tiền mặt.',
            NULL, 'CASH', @AdminId, @Now, @Now
        );
        SET @DamagePlanId=SCOPE_IDENTITY();
    END;

    INSERT INTO dbo.Feedback_Resolution_Options (plan_id, option_no, title, description)
    SELECT @DamagePlanId, 1, N'Bồi thường 120.000 đồng', N'Bồi thường bằng tiền mặt sau khi Manager duyệt và Admin ghi nhận quyết toán.'
    WHERE NOT EXISTS (SELECT 1 FROM dbo.Feedback_Resolution_Options WHERE plan_id=@DamagePlanId AND option_no=1);

    SELECT @DamageOptionId=option_id FROM dbo.Feedback_Resolution_Options WHERE plan_id=@DamagePlanId AND option_no=1;
    UPDATE dbo.Feedback_Resolution_Plans
    SET selected_option_id=@DamageOptionId
    WHERE plan_id=@DamagePlanId AND selected_option_id IS NULL;

    INSERT INTO dbo.Feedback_Resolution_Actions (option_id, action_type, description, amount, calculation_basis)
    SELECT @DamageOptionId, 'CUSTOMER_PAYOUT', N'Bồi thường cho bình gốm bị nứt.', 120000,
           N'Mức hai bên đã thống nhất trong hồ sơ demo, dựa trên tình trạng món đồ và thời gian sử dụng; cần Manager duyệt.'
    WHERE NOT EXISTS (SELECT 1 FROM dbo.Feedback_Resolution_Actions WHERE option_id=@DamageOptionId);
END;
