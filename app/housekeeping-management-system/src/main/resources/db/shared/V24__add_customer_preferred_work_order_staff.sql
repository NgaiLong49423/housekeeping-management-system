ALTER TABLE dbo.Feedback_Work_Orders
    ADD customer_preferred_staff_id BIGINT NULL;

EXEC sys.sp_executesql N'
    ALTER TABLE dbo.Feedback_Work_Orders
        ADD CONSTRAINT FK_Feedback_Work_Orders_Customer_Preferred_Staff
            FOREIGN KEY (customer_preferred_staff_id) REFERENCES dbo.Staff(staff_id);
';

