SET XACT_ABORT ON;

ALTER TABLE dbo.Bookings DROP CONSTRAINT CK_Bookings_Incident_Customer_Response;
ALTER TABLE dbo.Bookings ADD CONSTRAINT CK_Bookings_Incident_Customer_Response
    CHECK ((incident_customer_response IS NULL AND incident_customer_responded_at IS NULL)
        OR (incident_customer_response IN ('ACCEPTED', 'DECLINED', 'EXPIRED') AND incident_customer_responded_at IS NOT NULL));

ALTER TABLE dbo.Bookings DROP CONSTRAINT CK_Bookings_Work_Start;
ALTER TABLE dbo.Bookings ADD CONSTRAINT CK_Bookings_Work_Start
    CHECK (work_started_at IS NULL OR staff_arrived_at IS NOT NULL OR arrival_verified_at IS NOT NULL);

CREATE TABLE dbo.Booking_Delay_Proposals (
    proposal_id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_Booking_Delay_Proposals PRIMARY KEY,
    booking_id BIGINT NOT NULL,
    staff_id BIGINT NOT NULL,
    expected_arrival_at DATETIME2(0) NOT NULL,
    reason NVARCHAR(500) NOT NULL,
    proposed_at DATETIME2(0) NOT NULL,
    status VARCHAR(10) NOT NULL,
    responded_at DATETIME2(0) NULL,
    CONSTRAINT FK_Booking_Delay_Proposals_Bookings FOREIGN KEY (booking_id) REFERENCES dbo.Bookings(booking_id),
    CONSTRAINT FK_Booking_Delay_Proposals_Staff FOREIGN KEY (staff_id) REFERENCES dbo.Staff(staff_id),
    CONSTRAINT CK_Booking_Delay_Proposals_Status CHECK (status IN ('PENDING','ACCEPTED','DECLINED','EXPIRED')),
    CONSTRAINT CK_Booking_Delay_Proposals_Response CHECK ((status='PENDING' AND responded_at IS NULL) OR (status<>'PENDING' AND responded_at IS NOT NULL)),
    CONSTRAINT CK_Booking_Delay_Proposals_Reason CHECK (LEN(LTRIM(RTRIM(reason))) BETWEEN 1 AND 500)
);

CREATE INDEX IX_Booking_Delay_Proposals_Booking
    ON dbo.Booking_Delay_Proposals(booking_id, proposal_id DESC);
