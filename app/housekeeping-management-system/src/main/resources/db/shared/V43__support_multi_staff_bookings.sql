ALTER TABLE dbo.Services
    ADD additional_staff_price DECIMAL(12,2) NOT NULL
        CONSTRAINT DF_Services_Additional_Staff_Price DEFAULT (0),
    CONSTRAINT CK_Services_Additional_Staff_Price CHECK (additional_staff_price >= 0);

ALTER TABLE dbo.Booking_Details
    ADD additional_staff_price_snapshot DECIMAL(12,2) NOT NULL
        CONSTRAINT DF_Booking_Details_Additional_Staff_Price DEFAULT (0),
    staff_count_snapshot INT NOT NULL
        CONSTRAINT DF_Booking_Details_Staff_Count DEFAULT (1),
    CONSTRAINT CK_Booking_Details_Additional_Staff_Price CHECK (additional_staff_price_snapshot >= 0),
    CONSTRAINT CK_Booking_Details_Staff_Count CHECK (staff_count_snapshot >= 1);

CREATE TABLE dbo.Booking_Staff_Assignments (
    booking_staff_id BIGINT IDENTITY(1,1) NOT NULL,
    booking_id BIGINT NOT NULL,
    staff_id BIGINT NOT NULL,
    assignment_role VARCHAR(10) NOT NULL,
    assignment_status VARCHAR(20) NOT NULL CONSTRAINT DF_Booking_Staff_Assignments_Status DEFAULT ('ASSIGNED'),
    assigned_at DATETIME2(0) NOT NULL CONSTRAINT DF_Booking_Staff_Assignments_AssignedAt DEFAULT (SYSDATETIME()),
    arrived_at DATETIME2(0) NULL,
    CONSTRAINT PK_Booking_Staff_Assignments PRIMARY KEY (booking_staff_id),
    CONSTRAINT FK_Booking_Staff_Assignments_Booking FOREIGN KEY (booking_id) REFERENCES dbo.Bookings(booking_id),
    CONSTRAINT FK_Booking_Staff_Assignments_Staff FOREIGN KEY (staff_id) REFERENCES dbo.Staff(staff_id),
    CONSTRAINT CK_Booking_Staff_Assignments_Role CHECK (assignment_role IN ('LEAD', 'MEMBER')),
    CONSTRAINT CK_Booking_Staff_Assignments_Status CHECK (assignment_status IN ('ASSIGNED', 'REPLACED', 'REMOVED'))
);

INSERT INTO dbo.Booking_Staff_Assignments
    (booking_id, staff_id, assignment_role, assignment_status, assigned_at, arrived_at)
SELECT booking_id, staff_id, 'LEAD', 'ASSIGNED', created_at, staff_arrived_at
FROM dbo.Bookings;

CREATE UNIQUE INDEX UX_Booking_Staff_Assignments_Active_Member
    ON dbo.Booking_Staff_Assignments(booking_id, staff_id)
    WHERE assignment_status = 'ASSIGNED';

CREATE UNIQUE INDEX UX_Booking_Staff_Assignments_Active_Lead
    ON dbo.Booking_Staff_Assignments(booking_id)
    WHERE assignment_status = 'ASSIGNED' AND assignment_role = 'LEAD';

CREATE INDEX IX_Booking_Staff_Assignments_Staff_Status
    ON dbo.Booking_Staff_Assignments(staff_id, assignment_status, booking_id);

CREATE TABLE dbo.Booking_Team_Change_Requests (
    request_id BIGINT IDENTITY(1,1) NOT NULL,
    booking_id BIGINT NOT NULL,
    requested_by BIGINT NOT NULL,
    affected_staff_id BIGINT NOT NULL,
    proposed_staff_count INT NOT NULL,
    reason NVARCHAR(1000) NOT NULL,
    request_status VARCHAR(30) NOT NULL CONSTRAINT DF_Booking_Team_Change_Requests_Status DEFAULT ('PENDING'),
    resolution VARCHAR(30) NULL,
    created_at DATETIME2(0) NOT NULL CONSTRAINT DF_Booking_Team_Change_Requests_CreatedAt DEFAULT (SYSDATETIME()),
    resolved_at DATETIME2(0) NULL,
    resolved_by BIGINT NULL,
    CONSTRAINT PK_Booking_Team_Change_Requests PRIMARY KEY (request_id),
    CONSTRAINT FK_Booking_Team_Change_Requests_Booking FOREIGN KEY (booking_id) REFERENCES dbo.Bookings(booking_id),
    CONSTRAINT FK_Booking_Team_Change_Requests_Admin FOREIGN KEY (requested_by) REFERENCES dbo.Users(user_id),
    CONSTRAINT FK_Booking_Team_Change_Requests_Affected_Staff FOREIGN KEY (affected_staff_id) REFERENCES dbo.Staff(staff_id),
    CONSTRAINT FK_Booking_Team_Change_Requests_Resolver FOREIGN KEY (resolved_by) REFERENCES dbo.Users(user_id),
    CONSTRAINT CK_Booking_Team_Change_Requests_Count CHECK (proposed_staff_count >= 0),
    CONSTRAINT CK_Booking_Team_Change_Requests_Status CHECK (request_status IN ('PENDING', 'RESOLVED')),
    CONSTRAINT CK_Booking_Team_Change_Requests_Resolution CHECK (resolution IS NULL OR resolution IN ('PROCEEDED_SMALLER', 'RESCHEDULED', 'CANCELLED')),
    CONSTRAINT CK_Booking_Team_Change_Requests_Audit CHECK (
        (request_status='PENDING' AND resolution IS NULL AND resolved_at IS NULL AND resolved_by IS NULL)
        OR (request_status='RESOLVED' AND resolution IS NOT NULL AND resolved_at IS NOT NULL AND resolved_by IS NOT NULL)
    )
);

CREATE UNIQUE INDEX UX_Booking_Team_Change_Requests_Pending
    ON dbo.Booking_Team_Change_Requests(booking_id)
    WHERE request_status='PENDING';
