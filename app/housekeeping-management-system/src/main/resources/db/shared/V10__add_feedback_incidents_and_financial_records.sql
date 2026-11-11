SET XACT_ABORT ON;

ALTER TABLE dbo.Users ADD can_manage_incidents BIT NOT NULL
    CONSTRAINT DF_Users_CanManageIncidents DEFAULT (0);
-- Defer binding the newly added column until its ALTER TABLE has completed.
EXEC sys.sp_executesql N'
    ALTER TABLE dbo.Users ADD CONSTRAINT CK_Users_CanManageIncidentsRole
        CHECK (role=''ADMIN'' OR can_manage_incidents=0);
';
ALTER TABLE dbo.Payments ADD is_simulated BIT NOT NULL CONSTRAINT DF_Payments_IsSimulated DEFAULT (0);
-- Existing ONLINE transactions belong to the configured VNPay sandbox; do not count them as real cash flow.
EXEC sys.sp_executesql N'
    UPDATE dbo.Payments SET is_simulated=1 WHERE payment_method=''ONLINE'';
';

CREATE TABLE dbo.Feedback_Cases (
    case_id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_Feedback_Cases PRIMARY KEY,
    feedback_id BIGINT NOT NULL CONSTRAINT UQ_Feedback_Cases_Feedback UNIQUE
        CONSTRAINT FK_Feedback_Cases_Feedback REFERENCES dbo.Feedbacks(feedback_id),
    incident_types NVARCHAR(150) NOT NULL CONSTRAINT CK_Feedback_Cases_Types
        CHECK (LEN(incident_types)>0 AND incident_types NOT LIKE N'%[^A-Z_,]%'),
    customer_description NVARCHAR(MAX) NOT NULL,
    case_status VARCHAR(20) NOT NULL CONSTRAINT DF_Feedback_Cases_Status DEFAULT 'RECEIVED'
        CONSTRAINT CK_Feedback_Cases_Status CHECK (case_status IN ('RECEIVED','INVESTIGATING','VERIFIED','NOT_SUBSTANTIATED','RESOLVED')),
    customer_outcome NVARCHAR(MAX) NULL,
    remedy_type VARCHAR(30) NULL CONSTRAINT CK_Feedback_Cases_Remedy
        CHECK (remedy_type IS NULL OR remedy_type IN ('RE_CLEAN','REPAIR','REPLACE','PRICE_REDUCTION','REFUND','COMPENSATION','OTHER')),
    remedy_completed BIT NOT NULL CONSTRAINT DF_Feedback_Cases_RemedyCompleted DEFAULT (0),
    internal_finding NVARCHAR(MAX) NULL,
    staff_action VARCHAR(30) NULL CONSTRAINT CK_Feedback_Cases_StaffAction
        CHECK (staff_action IS NULL OR staff_action IN ('NONE','COACHING','PROPOSE_WARNING','PROPOSE_FINAL_WARNING','PROPOSE_RECOVERY','OTHER')),
    staff_action_status VARCHAR(20) NOT NULL CONSTRAINT DF_Feedback_Cases_StaffActionStatus DEFAULT 'NOT_PROPOSED'
        CONSTRAINT CK_Feedback_Cases_StaffActionStatus CHECK (staff_action_status IN ('NOT_PROPOSED','PENDING','APPROVED','REJECTED')),
    staff_action_by BIGINT NULL CONSTRAINT FK_Feedback_Cases_StaffActionBy REFERENCES dbo.Users(user_id),
    staff_action_approved_by BIGINT NULL CONSTRAINT FK_Feedback_Cases_StaffActionApprovedBy REFERENCES dbo.Users(user_id),
    reviewed_by BIGINT NULL CONSTRAINT FK_Feedback_Cases_Reviewer REFERENCES dbo.Users(user_id),
    reviewed_at DATETIME2(0) NULL,
    created_at DATETIME2(0) NOT NULL CONSTRAINT DF_Feedback_Cases_Created DEFAULT SYSDATETIME(),
    resolved_at DATETIME2(0) NULL,
    CONSTRAINT CK_Feedback_Cases_Resolution CHECK ((case_status='RESOLVED' AND resolved_at IS NOT NULL) OR (case_status<>'RESOLVED' AND resolved_at IS NULL)),
    CONSTRAINT CK_Feedback_Cases_ActionApproval CHECK ((staff_action_status IN ('NOT_PROPOSED','PENDING') AND staff_action_approved_by IS NULL) OR (staff_action_status IN ('APPROVED','REJECTED') AND staff_action_approved_by IS NOT NULL))
);

CREATE TABLE dbo.Feedback_Case_Evidence (
    evidence_id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_Feedback_Case_Evidence PRIMARY KEY,
    case_id BIGINT NOT NULL CONSTRAINT FK_Feedback_Case_Evidence_Case REFERENCES dbo.Feedback_Cases(case_id),
    image_path NVARCHAR(500) NOT NULL,
    uploaded_by BIGINT NOT NULL CONSTRAINT FK_Feedback_Case_Evidence_User REFERENCES dbo.Users(user_id),
    uploaded_at DATETIME2(0) NOT NULL CONSTRAINT DF_Feedback_Case_Evidence_Uploaded DEFAULT SYSDATETIME()
);

CREATE TABLE dbo.Staff_Incident_Actions (
    action_id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_Staff_Incident_Actions PRIMARY KEY,
    case_id BIGINT NOT NULL CONSTRAINT FK_Staff_Incident_Actions_Case REFERENCES dbo.Feedback_Cases(case_id),
    staff_id BIGINT NOT NULL CONSTRAINT FK_Staff_Incident_Actions_Staff REFERENCES dbo.Staff(staff_id),
    action_type VARCHAR(30) NOT NULL CONSTRAINT CK_Staff_Incident_Actions_Type CHECK (action_type IN ('NONE','COACHING','PROPOSE_WARNING','PROPOSE_FINAL_WARNING','PROPOSE_RECOVERY','OTHER')),
    status VARCHAR(20) NOT NULL CONSTRAINT DF_Staff_Incident_Actions_Status DEFAULT 'PENDING' CONSTRAINT CK_Staff_Incident_Actions_Status CHECK (status IN ('PENDING','APPROVED','REJECTED')),
    reason NVARCHAR(1000) NOT NULL,
    staff_notice NVARCHAR(1000) NULL,
    proposed_by BIGINT NOT NULL CONSTRAINT FK_Staff_Incident_Actions_Proposer REFERENCES dbo.Users(user_id),
    decided_by BIGINT NULL CONSTRAINT FK_Staff_Incident_Actions_Decider REFERENCES dbo.Users(user_id),
    created_at DATETIME2(0) NOT NULL CONSTRAINT DF_Staff_Incident_Actions_Created DEFAULT SYSDATETIME(),
    decided_at DATETIME2(0) NULL,
    acknowledged_at DATETIME2(0) NULL,
    CONSTRAINT CK_Staff_Incident_Actions_Decision CHECK ((status='PENDING' AND decided_by IS NULL AND decided_at IS NULL) OR (status<>'PENDING' AND decided_by IS NOT NULL AND decided_at IS NOT NULL)),
    CONSTRAINT CK_Staff_Incident_Actions_Separation CHECK (decided_by IS NULL OR decided_by<>proposed_by),
    CONSTRAINT CK_Staff_Incident_Actions_Notice CHECK (action_type='NONE' OR (staff_notice IS NOT NULL AND LEN(LTRIM(RTRIM(staff_notice)))>0)),
    CONSTRAINT CK_Staff_Incident_Actions_Acknowledged CHECK (acknowledged_at IS NULL OR status='APPROVED')
);

CREATE TABLE dbo.Booking_Financial_Entries (
    entry_id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_Booking_Financial_Entries PRIMARY KEY,
    booking_id BIGINT NOT NULL CONSTRAINT FK_Booking_Financial_Entries_Booking REFERENCES dbo.Bookings(booking_id),
    case_id BIGINT NULL CONSTRAINT FK_Booking_Financial_Entries_Case REFERENCES dbo.Feedback_Cases(case_id),
    entry_type VARCHAR(30) NOT NULL CONSTRAINT CK_Booking_Financial_Entries_Type CHECK (entry_type IN ('REFUND','PRICE_REDUCTION','REMEDIATION_COST','COMPENSATION','STAFF_RECOVERY')),
    amount DECIMAL(12,2) NOT NULL CONSTRAINT CK_Booking_Financial_Entries_Amount CHECK (amount>0),
    currency CHAR(3) NOT NULL CONSTRAINT DF_Booking_Financial_Entries_Currency DEFAULT 'VND' CONSTRAINT CK_Booking_Financial_Entries_Currency CHECK (currency='VND'),
    reason NVARCHAR(1000) NOT NULL,
    status VARCHAR(20) NOT NULL CONSTRAINT DF_Booking_Financial_Entries_Status DEFAULT 'PROPOSED'
        CONSTRAINT CK_Booking_Financial_Entries_Status CHECK (status IN ('PROPOSED','PENDING_APPROVAL','APPROVED','REJECTED','PAID','CANCELLED','SIMULATED')),
    payment_method VARCHAR(20) NULL CONSTRAINT CK_Booking_Financial_Entries_Method CHECK (payment_method IS NULL OR payment_method IN ('ONLINE','CASH','BANK_TRANSFER','OTHER')),
    transaction_reference NVARCHAR(255) NULL,
    receipt_path NVARCHAR(500) NULL,
    is_simulated BIT NOT NULL CONSTRAINT DF_Booking_Financial_Entries_Simulated DEFAULT (0),
    created_by BIGINT NOT NULL CONSTRAINT FK_Booking_Financial_Entries_Creator REFERENCES dbo.Users(user_id),
    approved_by BIGINT NULL CONSTRAINT FK_Booking_Financial_Entries_Approver REFERENCES dbo.Users(user_id),
    settled_by BIGINT NULL CONSTRAINT FK_Booking_Financial_Entries_Settler REFERENCES dbo.Users(user_id),
    created_at DATETIME2(0) NOT NULL CONSTRAINT DF_Booking_Financial_Entries_Created DEFAULT SYSDATETIME(),
    approved_at DATETIME2(0) NULL,
    paid_at DATETIME2(0) NULL,
    cancelled_by BIGINT NULL CONSTRAINT FK_Booking_Financial_Entries_CancelledBy REFERENCES dbo.Users(user_id),
    cancellation_reason NVARCHAR(1000) NULL,
    rejection_reason NVARCHAR(1000) NULL,
    CONSTRAINT CK_Booking_Financial_Entries_Approval CHECK ((status IN ('PROPOSED','PENDING_APPROVAL') AND approved_by IS NULL AND approved_at IS NULL) OR (status IN ('APPROVED','PAID','SIMULATED') AND approved_by IS NOT NULL AND approved_at IS NOT NULL) OR status IN ('REJECTED','CANCELLED')),
    CONSTRAINT CK_Booking_Financial_Entries_Paid CHECK ((status IN ('PAID','SIMULATED') AND paid_at IS NOT NULL AND settled_by IS NOT NULL AND payment_method IS NOT NULL AND receipt_path IS NOT NULL) OR (status NOT IN ('PAID','SIMULATED') AND paid_at IS NULL AND settled_by IS NULL)),
    CONSTRAINT CK_Booking_Financial_Entries_Simulation CHECK ((status='SIMULATED' AND is_simulated=1) OR (status<>'SIMULATED' AND (status<>'PAID' OR is_simulated=0))),
    CONSTRAINT CK_Booking_Financial_Entries_SeparateApprover CHECK (approved_by IS NULL OR approved_by<>created_by),
    CONSTRAINT CK_Booking_Financial_Entries_Cancelled CHECK ((status='CANCELLED' AND cancelled_by IS NOT NULL AND cancellation_reason IS NOT NULL) OR (status<>'CANCELLED' AND cancelled_by IS NULL AND cancellation_reason IS NULL))
);

CREATE INDEX IX_Feedback_Cases_Status ON dbo.Feedback_Cases(case_status, created_at);
CREATE INDEX IX_Booking_Financial_Entries_Booking ON dbo.Booking_Financial_Entries(booking_id, status, entry_type);
