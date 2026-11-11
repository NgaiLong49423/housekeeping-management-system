ALTER TABLE dbo.Feedback_Cases
    ADD item_description NVARCHAR(300) NULL,
        customer_estimated_value DECIMAL(12,2) NULL,
        item_age_months INT NULL;

ALTER TABLE dbo.Feedback_Cases
    ADD CONSTRAINT CK_Feedback_Cases_Customer_Estimated_Value
        CHECK (customer_estimated_value IS NULL OR customer_estimated_value > 0),
        CONSTRAINT CK_Feedback_Cases_Item_Age_Months
        CHECK (item_age_months IS NULL OR item_age_months BETWEEN 0 AND 1200);
