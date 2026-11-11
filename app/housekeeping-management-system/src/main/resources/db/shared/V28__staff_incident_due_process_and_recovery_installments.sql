ALTER TABLE dbo.Feedback_Cases DROP CONSTRAINT CK_Feedback_Cases_StaffActionStatus;
ALTER TABLE dbo.Feedback_Cases DROP CONSTRAINT CK_Feedback_Cases_ActionApproval;
ALTER TABLE dbo.Feedback_Cases ALTER COLUMN staff_action_status VARCHAR(30) NOT NULL;
ALTER TABLE dbo.Feedback_Cases ADD CONSTRAINT CK_Feedback_Cases_StaffActionStatus
    CHECK (staff_action_status IN ('NOT_PROPOSED','PENDING','APPROVED','REJECTED','AWAITING_STAFF','WAITING_MANAGER','WAITING_STAFF_APPEAL','WAITING_MANAGER_APPEAL','NO_LIABILITY','INTERNAL_ACTION','RECOVERY_DUE','RECOVERY_SETTLED','FINALIZED'));
ALTER TABLE dbo.Feedback_Cases ADD CONSTRAINT CK_Feedback_Cases_ActionApproval
    CHECK ((staff_action_status IN ('NOT_PROPOSED','PENDING','AWAITING_STAFF','WAITING_MANAGER') AND staff_action_approved_by IS NULL)
        OR (staff_action_status IN ('APPROVED','REJECTED','WAITING_STAFF_APPEAL','WAITING_MANAGER_APPEAL','NO_LIABILITY','INTERNAL_ACTION','RECOVERY_DUE','RECOVERY_SETTLED','FINALIZED') AND staff_action_approved_by IS NOT NULL));
ALTER TABLE dbo.Staff_Incident_Actions DROP CONSTRAINT CK_Staff_Incident_Actions_Acknowledged;
ALTER TABLE dbo.Staff_Incident_Actions DROP CONSTRAINT CK_Staff_Incident_Actions_Decision;
ALTER TABLE dbo.Staff_Incident_Actions DROP CONSTRAINT CK_Staff_Incident_Actions_Notice;
ALTER TABLE dbo.Staff_Incident_Actions DROP CONSTRAINT CK_Staff_Incident_Actions_Status;
ALTER TABLE dbo.Staff_Incident_Actions ALTER COLUMN status VARCHAR(30) NOT NULL;
ALTER TABLE dbo.Staff_Incident_Actions ADD CONSTRAINT CK_Staff_Incident_Actions_Status
    CHECK (status IN ('PENDING','APPROVED','REJECTED','AWAITING_STAFF','WAITING_MANAGER','WAITING_STAFF_APPEAL','WAITING_MANAGER_APPEAL','NO_LIABILITY','INTERNAL_ACTION','RECOVERY_DUE','RECOVERY_SETTLED','FINALIZED'));
ALTER TABLE dbo.Staff_Incident_Actions ADD CONSTRAINT CK_Staff_Incident_Actions_Decision
    CHECK ((status IN ('PENDING','AWAITING_STAFF','WAITING_MANAGER') AND decided_by IS NULL AND decided_at IS NULL)
        OR (status NOT IN ('PENDING','AWAITING_STAFF','WAITING_MANAGER') AND decided_by IS NOT NULL AND decided_at IS NOT NULL));
ALTER TABLE dbo.Staff_Incident_Actions ADD CONSTRAINT CK_Staff_Incident_Actions_Acknowledged
    CHECK (acknowledged_at IS NULL OR status IN ('APPROVED','FINALIZED','NO_LIABILITY','INTERNAL_ACTION','RECOVERY_DUE','RECOVERY_SETTLED'));
ALTER TABLE dbo.Staff_Incident_Actions ADD
    response_deadline DATETIME2(0) NULL,
    staff_response_expired_at DATETIME2(0) NULL,
    staff_response NVARCHAR(4000) NULL,
    staff_responded_at DATETIME2(0) NULL,
    appeal_text NVARCHAR(4000) NULL,
    appealed_at DATETIME2(0) NULL,
    decision_reason NVARCHAR(1000) NULL,
    liability_amount DECIMAL(12,2) NULL,
    finalized_at DATETIME2(0) NULL,
    appeal_decided_by BIGINT NULL,
    appeal_decided_at DATETIME2(0) NULL,
    decision_outcome VARCHAR(20) NULL CONSTRAINT CK_Staff_Incident_Actions_Outcome CHECK (decision_outcome IS NULL OR decision_outcome IN ('NO_LIABILITY','INTERNAL_ACTION','RECOVERY')),
    CONSTRAINT FK_Staff_Incident_Actions_AppealManager FOREIGN KEY (appeal_decided_by) REFERENCES dbo.Users(user_id),
    CONSTRAINT CK_Staff_Incident_Actions_OutcomeLiability CHECK (decision_outcome IS NULL
        OR (decision_outcome='RECOVERY' AND liability_amount > 0)
        OR (decision_outcome IN ('NO_LIABILITY','INTERNAL_ACTION') AND COALESCE(liability_amount,0)=0)),
    CONSTRAINT CK_Staff_Incident_Actions_RecoveryStatus CHECK (status NOT IN ('RECOVERY_DUE','RECOVERY_SETTLED')
        OR (decision_outcome='RECOVERY' AND liability_amount > 0));

CREATE TABLE dbo.Staff_Incident_Action_Evidence (
    evidence_id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_Staff_Incident_Action_Evidence PRIMARY KEY,
    action_id BIGINT NOT NULL,
    uploaded_by BIGINT NOT NULL,
    evidence_stage VARCHAR(20) NOT NULL CONSTRAINT CK_Staff_Action_Evidence_Stage CHECK (evidence_stage IN ('RESPONSE','APPEAL')),
    media_type VARCHAR(20) NOT NULL,
    content_type VARCHAR(100) NULL,
    original_name NVARCHAR(255) NULL,
    storage_path NVARCHAR(500) NOT NULL,
    uploaded_at DATETIME2(0) NOT NULL CONSTRAINT DF_Staff_Action_Evidence_Uploaded DEFAULT SYSDATETIME(),
    CONSTRAINT FK_Staff_Action_Evidence_Action FOREIGN KEY (action_id) REFERENCES dbo.Staff_Incident_Actions(action_id),
    CONSTRAINT FK_Staff_Action_Evidence_User FOREIGN KEY (uploaded_by) REFERENCES dbo.Users(user_id)
);

CREATE TABLE dbo.Staff_Recovery_Installments (
    installment_id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_Staff_Recovery_Installments PRIMARY KEY,
    action_id BIGINT NOT NULL,
    amount DECIMAL(12,2) NOT NULL CONSTRAINT CK_Staff_Recovery_Installments_Amount CHECK (amount > 0),
    payment_method VARCHAR(20) NOT NULL CONSTRAINT CK_Staff_Recovery_Installments_Method CHECK (payment_method IN ('CASH','BANK_TRANSFER','OTHER')),
    transaction_reference NVARCHAR(255) NOT NULL,
    receipt_path NVARCHAR(500) NOT NULL,
    received_by BIGINT NOT NULL,
    received_at DATETIME2(0) NOT NULL CONSTRAINT DF_Staff_Recovery_Installments_Received DEFAULT SYSDATETIME(),
    CONSTRAINT FK_Staff_Recovery_Installments_Action FOREIGN KEY (action_id) REFERENCES dbo.Staff_Incident_Actions(action_id),
    CONSTRAINT FK_Staff_Recovery_Installments_User FOREIGN KEY (received_by) REFERENCES dbo.Users(user_id)
);
