-- The application stores reviewed evidence IDs as a comma-separated list; no evidence is an empty string.
IF UPPER(DB_NAME()) = N'HOME_CLEANING_DEMO'
BEGIN
    UPDATE verification
    SET reviewed_evidence_ids=N''
    FROM dbo.Feedback_Case_Verifications verification
    JOIN dbo.Feedback_Cases incident ON incident.case_id=verification.case_id
    JOIN dbo.Feedbacks feedback ON feedback.feedback_id=incident.feedback_id
    WHERE incident.resolution_workflow_required=1
      AND verification.reviewed_evidence_ids=N'[]'
      AND feedback.customer_id=(
          SELECT user_id FROM dbo.Users
          WHERE email=N'customer@demo.gonnha.local' AND role='CUSTOMER'
      )
      AND feedback.content IN (
          N'Một số khu vực vẫn còn bụi sau khi dọn. Tôi muốn công ty kiểm tra và đề xuất cách khắc phục.',
          N'Món đồ trang trí bị nứt trong lúc làm dịch vụ. Đây là thông tin ban đầu để công ty xác minh.'
      );
END;
