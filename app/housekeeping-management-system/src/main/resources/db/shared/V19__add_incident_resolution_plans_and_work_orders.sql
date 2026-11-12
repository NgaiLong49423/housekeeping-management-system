ALTER TABLE dbo.Feedback_Case_Evidence ALTER COLUMN image_path NVARCHAR(500) NULL;
ALTER TABLE dbo.Feedback_Case_Evidence ADD media_type VARCHAR(20) NOT NULL
    CONSTRAINT DF_Feedback_Case_Evidence_Media_Type DEFAULT 'IMAGE',
    content_type VARCHAR(100) NULL,
    original_name NVARCHAR(255) NULL,
    external_url NVARCHAR(1000) NULL;
EXEC sys.sp_executesql N'
    ALTER TABLE dbo.Feedback_Case_Evidence ADD CONSTRAINT CK_Feedback_Case_Evidence_Media_Source
        CHECK ((image_path IS NOT NULL AND external_url IS NULL) OR (image_path IS NULL AND external_url IS NOT NULL));
    ALTER TABLE dbo.Feedback_Case_Evidence ADD CONSTRAINT CK_Feedback_Case_Evidence_Media_Type
        CHECK (media_type IN (''IMAGE'',''PDF'',''VIDEO'',''DRIVE_LINK''));
';
ALTER TABLE dbo.Feedback_Cases ADD resolution_workflow_required BIT NOT NULL
    CONSTRAINT DF_Feedback_Cases_Resolution_Workflow DEFAULT 0;

CREATE TABLE dbo.Feedback_Case_Verifications (
    verification_id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_Feedback_Case_Verifications PRIMARY KEY,
    case_id BIGINT NOT NULL,
    admin_id BIGINT NOT NULL,
    case_status VARCHAR(30) NOT NULL,
    reviewed_evidence_ids NVARCHAR(2000) NOT NULL,
    internal_finding NVARCHAR(4000) NULL,
    created_at DATETIME2(0) NOT NULL CONSTRAINT DF_Feedback_Case_Verifications_Created DEFAULT SYSDATETIME(),
    CONSTRAINT FK_Feedback_Case_Verifications_Case FOREIGN KEY(case_id) REFERENCES dbo.Feedback_Cases(case_id),
    CONSTRAINT FK_Feedback_Case_Verifications_Admin FOREIGN KEY(admin_id) REFERENCES dbo.Users(user_id),
    CONSTRAINT CK_Feedback_Case_Verifications_Status CHECK(case_status IN ('INVESTIGATING','VERIFIED','NOT_SUBSTANTIATED'))
);
CREATE INDEX IX_Feedback_Case_Verifications_Case_Date ON dbo.Feedback_Case_Verifications(case_id,created_at);

CREATE TABLE dbo.Feedback_Resolution_Plans (
    plan_id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_Feedback_Resolution_Plans PRIMARY KEY,
    case_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    status VARCHAR(32) NOT NULL CONSTRAINT DF_Feedback_Resolution_Plans_Status DEFAULT 'DRAFT',
    internal_finding NVARCHAR(4000) NULL,
    customer_response NVARCHAR(2000) NULL,
    completion_feedback NVARCHAR(1000) NULL,
    customer_completed_at DATETIME2(0) NULL,
    selected_option_id BIGINT NULL,
    customer_payout_method VARCHAR(20) NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME2(0) NOT NULL CONSTRAINT DF_Feedback_Resolution_Plans_Created DEFAULT SYSDATETIME(),
    responded_at DATETIME2(0) NULL,
    manager_id BIGINT NULL,
    manager_decision_at DATETIME2(0) NULL,
    manager_reason NVARCHAR(2000) NULL,
    CONSTRAINT FK_Feedback_Resolution_Plans_Case FOREIGN KEY(case_id) REFERENCES dbo.Feedback_Cases(case_id),
    CONSTRAINT UQ_Feedback_Resolution_Plans_Version UNIQUE(case_id, version_no),
    CONSTRAINT CK_Feedback_Resolution_Plans_Status CHECK(status IN ('DRAFT','AWAITING_CUSTOMER','CUSTOMER_SELECTED','NEEDS_REVISION','MANAGER_REVIEW','RETURNED','APPROVED','IN_PROGRESS','AWAITING_CUSTOMER_CONFIRMATION','COMPLETED','CUSTOMER_DISPUTED','CLOSED')),
    CONSTRAINT CK_Feedback_Resolution_Plans_Payout CHECK(customer_payout_method IS NULL OR customer_payout_method IN ('CASH','BANK_TRANSFER'))
);

CREATE TABLE dbo.Feedback_Resolution_Options (
    option_id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_Feedback_Resolution_Options PRIMARY KEY,
    plan_id BIGINT NOT NULL,
    option_no INT NOT NULL,
    title NVARCHAR(150) NOT NULL,
    description NVARCHAR(2000) NOT NULL,
    CONSTRAINT FK_Feedback_Resolution_Options_Plan FOREIGN KEY(plan_id) REFERENCES dbo.Feedback_Resolution_Plans(plan_id),
    CONSTRAINT UQ_Feedback_Resolution_Options_Number UNIQUE(plan_id, option_no),
    CONSTRAINT CK_Feedback_Resolution_Options_Number CHECK(option_no BETWEEN 1 AND 3)
);

CREATE TABLE dbo.Feedback_Resolution_Actions (
    action_id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_Feedback_Resolution_Actions PRIMARY KEY,
    option_id BIGINT NOT NULL,
    action_type VARCHAR(20) NOT NULL,
    description NVARCHAR(1000) NOT NULL,
    due_at DATETIME2(0) NULL,
    amount DECIMAL(12,2) NULL,
    calculation_basis NVARCHAR(1000) NULL,
    CONSTRAINT FK_Feedback_Resolution_Actions_Option FOREIGN KEY(option_id) REFERENCES dbo.Feedback_Resolution_Options(option_id),
    CONSTRAINT CK_Feedback_Resolution_Actions_Type CHECK(action_type IN ('INTERNAL_WORK','CUSTOMER_PAYOUT')),
    CONSTRAINT CK_Feedback_Resolution_Actions_Amount CHECK((action_type='INTERNAL_WORK' AND amount IS NULL) OR (action_type='CUSTOMER_PAYOUT' AND amount>0))
);

CREATE TABLE dbo.Feedback_Work_Orders (
    work_order_id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_Feedback_Work_Orders PRIMARY KEY,
    case_id BIGINT NOT NULL,
    action_id BIGINT NOT NULL,
    assigned_staff_id BIGINT NULL,
    scheduled_at DATETIME2(0) NULL,
    scope NVARCHAR(2000) NOT NULL,
    estimated_cost DECIMAL(12,2) NULL,
    estimated_materials NVARCHAR(1000) NULL,
    actual_cost DECIMAL(12,2) NULL,
    actual_materials NVARCHAR(1000) NULL,
    completion_evidence NVARCHAR(1000) NULL,
    status VARCHAR(20) NOT NULL CONSTRAINT DF_Feedback_Work_Orders_Status DEFAULT 'OPEN',
    customer_confirmation VARCHAR(20) NULL,
    customer_feedback NVARCHAR(1000) NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME2(0) NOT NULL CONSTRAINT DF_Feedback_Work_Orders_Created DEFAULT SYSDATETIME(),
    completed_at DATETIME2(0) NULL,
    CONSTRAINT FK_Feedback_Work_Orders_Case FOREIGN KEY(case_id) REFERENCES dbo.Feedback_Cases(case_id),
    CONSTRAINT FK_Feedback_Work_Orders_Action FOREIGN KEY(action_id) REFERENCES dbo.Feedback_Resolution_Actions(action_id),
    CONSTRAINT UQ_Feedback_Work_Orders_Action UNIQUE(action_id),
    CONSTRAINT CK_Feedback_Work_Orders_Status CHECK(status IN ('OPEN','SCHEDULED','COMPLETED','CUSTOMER_CONFIRMED','DISPUTED')),
    CONSTRAINT CK_Feedback_Work_Orders_Confirmation CHECK(customer_confirmation IS NULL OR customer_confirmation IN ('CONFIRMED','DISPUTED'))
);

ALTER TABLE dbo.Booking_Financial_Entries ADD resolution_action_id BIGINT NULL;
ALTER TABLE dbo.Booking_Financial_Entries ADD CONSTRAINT FK_Booking_Financial_Entries_Resolution_Action
    FOREIGN KEY(resolution_action_id) REFERENCES dbo.Feedback_Resolution_Actions(action_id);
CREATE INDEX IX_Feedback_Resolution_Plans_Case_Status ON dbo.Feedback_Resolution_Plans(case_id,status,version_no);
CREATE INDEX IX_Feedback_Resolution_Options_Plan ON dbo.Feedback_Resolution_Options(plan_id,option_no);
CREATE INDEX IX_Feedback_Work_Orders_Case_Status ON dbo.Feedback_Work_Orders(case_id,status);
