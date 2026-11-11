-- Correct demo fixture topic codes to match FeedbackTopic enum values consumed by the portal.
IF UPPER(DB_NAME()) = N'HOME_CLEANING_DEMO'
BEGIN
    UPDATE feedback
    SET topics = CASE incident.incident_types
        WHEN N'CLEANLINESS' THEN N'CLEANING_QUALITY'
        WHEN N'PROPERTY_DAMAGE' THEN N'OTHER'
    END
    FROM dbo.Feedbacks feedback
    JOIN dbo.Feedback_Cases incident ON incident.feedback_id=feedback.feedback_id
    WHERE incident.resolution_workflow_required=1
      AND feedback.customer_id=(
          SELECT user_id FROM dbo.Users
          WHERE email=N'customer@demo.gonnha.local' AND role='CUSTOMER'
      )
      AND feedback.content IN (
          N'Một số khu vực vẫn còn bụi sau khi dọn. Tôi muốn công ty kiểm tra và đề xuất cách khắc phục.',
          N'Món đồ trang trí bị nứt trong lúc làm dịch vụ. Đây là thông tin ban đầu để công ty xác minh.'
      )
      AND incident.incident_types IN (N'CLEANLINESS', N'PROPERTY_DAMAGE')
      AND feedback.topics<>CASE incident.incident_types
          WHEN N'CLEANLINESS' THEN N'CLEANING_QUALITY'
          WHEN N'PROPERTY_DAMAGE' THEN N'OTHER'
      END;
END;
