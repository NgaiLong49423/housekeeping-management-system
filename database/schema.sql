-- Snapshot/reference only. Flyway migrations under app/housekeeping-management-system/src/main/resources/db/migration are authoritative.

CREATE TABLE Customer (
    customer_id BIGINT IDENTITY(1,1) NOT NULL,
    display_name NVARCHAR(150) NOT NULL,
    email NVARCHAR(254) COLLATE Latin1_General_100_CI_AS NOT NULL,
    password_hash NVARCHAR(255) NOT NULL,
    phone_number NVARCHAR(20) NULL,
    CONSTRAINT PK_Customer PRIMARY KEY (customer_id),
    CONSTRAINT UQ_Customer_Email UNIQUE (email)
);

CREATE TABLE Staff (
    staff_id BIGINT IDENTITY(1,1) NOT NULL,
    display_name NVARCHAR(150) NOT NULL,
    email NVARCHAR(254) COLLATE Latin1_General_100_CI_AS NOT NULL,
    password_hash NVARCHAR(255) NOT NULL,
    phone_number NVARCHAR(20) NULL,
    CONSTRAINT PK_Staff PRIMARY KEY (staff_id),
    CONSTRAINT UQ_Staff_Email UNIQUE (email)
);

CREATE TABLE Admin (
    admin_id BIGINT IDENTITY(1,1) NOT NULL,
    display_name NVARCHAR(150) NOT NULL,
    email NVARCHAR(254) COLLATE Latin1_General_100_CI_AS NOT NULL,
    password_hash NVARCHAR(255) NOT NULL,
    CONSTRAINT PK_Admin PRIMARY KEY (admin_id),
    CONSTRAINT UQ_Admin_Email UNIQUE (email)
);

CREATE TABLE Services (
    service_id BIGINT IDENTITY(1,1) NOT NULL,
    service_name NVARCHAR(150) NOT NULL,
    description NVARCHAR(1000) NULL,
    duration_minutes INT NOT NULL,
    base_price DECIMAL(18,0) NOT NULL,
    CONSTRAINT PK_Services PRIMARY KEY (service_id),
    CONSTRAINT CK_Services_Duration_Positive CHECK (duration_minutes > 0),
    CONSTRAINT CK_Services_BasePrice_NonNegative CHECK (base_price >= 0)
);

CREATE TABLE Booking (
    booking_id BIGINT IDENTITY(1,1) NOT NULL,
    customer_id BIGINT NOT NULL,
    start_at DATETIMEOFFSET(0) NOT NULL,
    end_at DATETIMEOFFSET(0) NOT NULL,
    booking_status VARCHAR(20) NOT NULL,
    created_at DATETIMEOFFSET(0) NOT NULL,
    CONSTRAINT PK_Booking PRIMARY KEY (booking_id),
    CONSTRAINT FK_Booking_Customer FOREIGN KEY (customer_id) REFERENCES Customer(customer_id) ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT CK_Booking_TimeRange CHECK (end_at > start_at),
    CONSTRAINT CK_Booking_Status CHECK (booking_status IN ('CONFIRMED', 'CANCELLED', 'COMPLETED'))
);

CREATE TABLE Work_Schedule (
    work_schedule_id BIGINT IDENTITY(1,1) NOT NULL,
    staff_id BIGINT NOT NULL,
    managed_by_admin_id BIGINT NOT NULL,
    available_start DATETIMEOFFSET(0) NOT NULL,
    available_end DATETIMEOFFSET(0) NOT NULL,
    CONSTRAINT PK_Work_Schedule PRIMARY KEY (work_schedule_id),
    CONSTRAINT FK_Work_Schedule_Staff FOREIGN KEY (staff_id) REFERENCES Staff(staff_id) ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT FK_Work_Schedule_Admin FOREIGN KEY (managed_by_admin_id) REFERENCES Admin(admin_id) ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT CK_Work_Schedule_TimeRange CHECK (available_end > available_start)
);

CREATE TABLE Booking_Detail (
    booking_detail_id BIGINT IDENTITY(1,1) NOT NULL,
    booking_id BIGINT NOT NULL,
    work_schedule_id BIGINT NOT NULL,
    service_id BIGINT NOT NULL,
    sequence_no INT NOT NULL,
    duration_minutes_snapshot INT NOT NULL,
    unit_price_snapshot DECIMAL(18,0) NOT NULL,
    CONSTRAINT PK_Booking_Detail PRIMARY KEY (booking_detail_id),
    CONSTRAINT FK_Booking_Detail_Booking FOREIGN KEY (booking_id) REFERENCES Booking(booking_id) ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT FK_Booking_Detail_Work_Schedule FOREIGN KEY (work_schedule_id) REFERENCES Work_Schedule(work_schedule_id) ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT FK_Booking_Detail_Services FOREIGN KEY (service_id) REFERENCES Services(service_id) ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT UQ_Booking_Detail_Booking_Sequence UNIQUE (booking_id, sequence_no),
    CONSTRAINT CK_Booking_Detail_Sequence_Positive CHECK (sequence_no > 0),
    CONSTRAINT CK_Booking_Detail_Duration_Positive CHECK (duration_minutes_snapshot > 0),
    CONSTRAINT CK_Booking_Detail_UnitPrice_NonNegative CHECK (unit_price_snapshot >= 0)
);

CREATE TABLE Payment (
    payment_id BIGINT IDENTITY(1,1) NOT NULL,
    booking_id BIGINT NOT NULL,
    payment_method VARCHAR(20) NULL,
    payment_status VARCHAR(10) NOT NULL,
    amount DECIMAL(18,0) NOT NULL,
    created_at DATETIMEOFFSET(0) NOT NULL,
    verified_at DATETIMEOFFSET(0) NULL,
    CONSTRAINT PK_Payment PRIMARY KEY (payment_id),
    CONSTRAINT FK_Payment_Booking FOREIGN KEY (booking_id) REFERENCES Booking(booking_id) ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT UQ_Payment_Booking UNIQUE (booking_id),
    CONSTRAINT CK_Payment_Method CHECK (payment_method IS NULL OR payment_method IN ('CASH', 'BANK_TRANSFER', 'PAYOS', 'MOMO')),
    CONSTRAINT CK_Payment_Status CHECK (payment_status IN ('UNPAID', 'PAID')),
    CONSTRAINT CK_Payment_Amount_NonNegative CHECK (amount >= 0),
    CONSTRAINT CK_Payment_Status_Invariants CHECK (
        (payment_status = 'PAID' AND payment_method IS NOT NULL AND verified_at IS NOT NULL)
        OR (payment_status = 'UNPAID' AND verified_at IS NULL)
    )
);

CREATE TABLE Payment_Attempt (
    attempt_id BIGINT IDENTITY(1,1) NOT NULL,
    payment_id BIGINT NOT NULL,
    provider_order_id VARCHAR(64) NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    provider_transaction_id VARCHAR(100) NULL,
    provider_payment_link_id VARCHAR(100) NULL,
    amount DECIMAL(18,0) NOT NULL,
    attempt_status VARCHAR(12) NOT NULL,
    checkout_url NVARCHAR(2000) NULL,
    created_at DATETIMEOFFSET(0) NOT NULL,
    updated_at DATETIMEOFFSET(0) NOT NULL,
    CONSTRAINT PK_Payment_Attempt PRIMARY KEY (attempt_id),
    CONSTRAINT FK_Payment_Attempt_Payment FOREIGN KEY (payment_id) REFERENCES Payment(payment_id)
        ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT UQ_Payment_Attempt_Order UNIQUE (provider_order_id),
    CONSTRAINT UQ_Payment_Attempt_Request UNIQUE (request_id),
    CONSTRAINT CK_Payment_Attempt_Status CHECK (attempt_status IN ('INITIATING', 'PENDING', 'FAILED', 'SUCCEEDED')),
    CONSTRAINT CK_Payment_Attempt_Amount CHECK (amount > 0)
);

CREATE INDEX IX_Payment_Attempt_Payment_Created
    ON Payment_Attempt(payment_id, created_at DESC, attempt_id DESC);

CREATE SEQUENCE dbo.PayOS_Order_Code_Seq
    AS BIGINT START WITH 100000000 INCREMENT BY 1 MINVALUE 1 MAXVALUE 2147483647 NO CYCLE;

CREATE TABLE Feedback (
    feedback_id BIGINT IDENTITY(1,1) NOT NULL,
    booking_id BIGINT NOT NULL,
    rating TINYINT NOT NULL,
    comment NVARCHAR(2000) NOT NULL,
    created_at DATETIMEOFFSET(0) NOT NULL,
    CONSTRAINT PK_Feedback PRIMARY KEY (feedback_id),
    CONSTRAINT FK_Feedback_Booking FOREIGN KEY (booking_id) REFERENCES Booking(booking_id) ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT UQ_Feedback_Booking UNIQUE (booking_id),
    CONSTRAINT CK_Feedback_Rating CHECK (rating BETWEEN 1 AND 5)
);

CREATE TABLE Favorite_Staff (
    customer_id BIGINT NOT NULL,
    staff_id BIGINT NOT NULL,
    created_at DATETIMEOFFSET(0) NOT NULL,
    CONSTRAINT PK_Favorite_Staff PRIMARY KEY (customer_id, staff_id),
    CONSTRAINT FK_Favorite_Staff_Customer FOREIGN KEY (customer_id) REFERENCES Customer(customer_id) ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT FK_Favorite_Staff_Staff FOREIGN KEY (staff_id) REFERENCES Staff(staff_id) ON DELETE NO ACTION ON UPDATE NO ACTION
);

CREATE TABLE Evidences (
    evidence_id BIGINT IDENTITY(1,1) NOT NULL,
    booking_detail_id BIGINT NOT NULL,
    staff_id BIGINT NOT NULL,
    storage_path NVARCHAR(500) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    uploaded_at DATETIMEOFFSET(0) NOT NULL,
    CONSTRAINT PK_Evidences PRIMARY KEY (evidence_id),
    CONSTRAINT FK_Evidences_Booking_Detail FOREIGN KEY (booking_detail_id) REFERENCES Booking_Detail(booking_detail_id) ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT FK_Evidences_Staff FOREIGN KEY (staff_id) REFERENCES Staff(staff_id) ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT UQ_Evidences_Storage_Path UNIQUE (storage_path),
    CONSTRAINT CK_Evidences_FileSize_Positive CHECK (file_size_bytes > 0)
);
