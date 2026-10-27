-- Fresh, isolated SQL Server baseline for the approved 15-table Home Cleaning model.
-- This migration is intentionally separate from the immutable legacy V1-V3 history.
SET XACT_ABORT ON;

IF UPPER(DB_NAME()) NOT IN (N'HOME_CLEANING_DEMO', N'HOME_CLEANING_DEV', N'HOME_CLEANING_ACCEPTANCE')
    THROW 51100, 'Refusing to apply the new Home Cleaning baseline outside an approved disposable database.', 1;

IF EXISTS (
    SELECT 1
    FROM sys.tables
    WHERE is_ms_shipped = 0
      AND UPPER(name) <> N'FLYWAY_SCHEMA_HISTORY'
)
    THROW 51101, 'Refusing to apply the fresh baseline to a database that already contains user tables.', 1;

CREATE TABLE dbo.Users (
    user_id BIGINT IDENTITY(1,1) NOT NULL,
    full_name NVARCHAR(150) NOT NULL,
    email NVARCHAR(255) COLLATE Latin1_General_100_CI_AS NOT NULL,
    password_hash NVARCHAR(255) NOT NULL,
    phone NVARCHAR(20) NOT NULL,
    role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL CONSTRAINT DF_Users_Status DEFAULT ('ACTIVE'),
    CONSTRAINT PK_Users PRIMARY KEY (user_id),
    CONSTRAINT UQ_Users_Email UNIQUE (email),
    CONSTRAINT CK_Users_Role CHECK (role IN ('CUSTOMER', 'STAFF', 'ADMIN')),
    CONSTRAINT CK_Users_Status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE dbo.Staff (
    staff_id BIGINT IDENTITY(1,1) NOT NULL,
    user_id BIGINT NOT NULL,
    staff_type VARCHAR(20) NOT NULL,
    CONSTRAINT PK_Staff PRIMARY KEY (staff_id),
    CONSTRAINT UQ_Staff_User UNIQUE (user_id),
    CONSTRAINT CK_Staff_Type CHECK (staff_type IN ('FULL_TIME', 'PART_TIME')),
    CONSTRAINT FK_Staff_User FOREIGN KEY (user_id) REFERENCES dbo.Users(user_id) ON DELETE NO ACTION
);

CREATE TABLE dbo.Staff_Profiles (
    profile_id BIGINT IDENTITY(1,1) NOT NULL,
    staff_id BIGINT NOT NULL,
    avatar_url NVARCHAR(500) NULL,
    bio NVARCHAR(MAX) NULL,
    CONSTRAINT PK_Staff_Profiles PRIMARY KEY (profile_id),
    CONSTRAINT UQ_Staff_Profiles_Staff UNIQUE (staff_id),
    CONSTRAINT FK_Staff_Profiles_Staff FOREIGN KEY (staff_id) REFERENCES dbo.Staff(staff_id) ON DELETE NO ACTION
);

CREATE TABLE dbo.Services (
    service_id BIGINT IDENTITY(1,1) NOT NULL,
    service_name NVARCHAR(150) NOT NULL,
    description NVARCHAR(MAX) NULL,
    image_url NVARCHAR(500) NULL,
    price DECIMAL(12,2) NOT NULL,
    duration_minutes INT NOT NULL,
    status VARCHAR(20) NOT NULL CONSTRAINT DF_Services_Status DEFAULT ('ACTIVE'),
    CONSTRAINT PK_Services PRIMARY KEY (service_id),
    CONSTRAINT CK_Services_Price CHECK (price >= 0),
    CONSTRAINT CK_Services_Duration CHECK (duration_minutes > 0),
    CONSTRAINT CK_Services_Status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE dbo.Company_Settings (
    company_id BIGINT IDENTITY(1,1) NOT NULL,
    company_address NVARCHAR(500) NOT NULL,
    latitude DECIMAL(10,7) NOT NULL,
    longitude DECIMAL(10,7) NOT NULL,
    service_radius_km DECIMAL(6,2) NOT NULL,
    CONSTRAINT PK_Company_Settings PRIMARY KEY (company_id),
    CONSTRAINT CK_Company_Settings_Latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT CK_Company_Settings_Longitude CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT CK_Company_Settings_Radius CHECK (service_radius_km > 0)
);

CREATE TABLE dbo.Company_Operating_Hours (
    operating_hour_id BIGINT IDENTITY(1,1) NOT NULL,
    company_id BIGINT NOT NULL,
    day_of_week INT NOT NULL,
    opening_time TIME(0) NULL,
    closing_time TIME(0) NULL,
    is_closed BIT NOT NULL CONSTRAINT DF_Company_Operating_Hours_Closed DEFAULT (0),
    CONSTRAINT PK_Company_Operating_Hours PRIMARY KEY (operating_hour_id),
    CONSTRAINT UQ_Company_Operating_Hours_Company_Day UNIQUE (company_id, day_of_week),
    CONSTRAINT CK_Company_Operating_Hours_Day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT CK_Company_Operating_Hours_Times CHECK (
        (is_closed = 1 AND opening_time IS NULL AND closing_time IS NULL)
        OR (is_closed = 0 AND opening_time IS NOT NULL AND closing_time IS NOT NULL AND opening_time < closing_time)
    ),
    CONSTRAINT FK_Company_Operating_Hours_Company FOREIGN KEY (company_id)
        REFERENCES dbo.Company_Settings(company_id) ON DELETE NO ACTION
);

CREATE TABLE dbo.Houses (
    house_id BIGINT IDENTITY(1,1) NOT NULL,
    customer_id BIGINT NOT NULL,
    house_name NVARCHAR(100) NOT NULL,
    address NVARCHAR(500) NOT NULL,
    latitude DECIMAL(10,7) NOT NULL,
    longitude DECIMAL(10,7) NOT NULL,
    address_note NVARCHAR(500) NULL,
    is_deleted BIT NOT NULL CONSTRAINT DF_Houses_Is_Deleted DEFAULT (0),
    CONSTRAINT PK_Houses PRIMARY KEY (house_id),
    CONSTRAINT CK_Houses_Latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT CK_Houses_Longitude CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT FK_Houses_Customer FOREIGN KEY (customer_id) REFERENCES dbo.Users(user_id) ON DELETE NO ACTION
);
CREATE INDEX IX_Houses_Customer_Deleted ON dbo.Houses(customer_id, is_deleted);

CREATE TABLE dbo.Staff_Availability (
    availability_id BIGINT IDENTITY(1,1) NOT NULL,
    staff_id BIGINT NOT NULL,
    work_date DATE NOT NULL,
    start_time TIME(0) NOT NULL,
    end_time TIME(0) NOT NULL,
    created_at DATETIME2(0) NOT NULL CONSTRAINT DF_Staff_Availability_Created DEFAULT (SYSDATETIME()),
    CONSTRAINT PK_Staff_Availability PRIMARY KEY (availability_id),
    CONSTRAINT CK_Staff_Availability_Time CHECK (start_time < end_time),
    CONSTRAINT FK_Staff_Availability_Staff FOREIGN KEY (staff_id) REFERENCES dbo.Staff(staff_id) ON DELETE NO ACTION
);
CREATE INDEX IX_Staff_Availability_Date ON dbo.Staff_Availability(staff_id, work_date, start_time, end_time);

CREATE TABLE dbo.Staff_Leaves (
    leave_id BIGINT IDENTITY(1,1) NOT NULL,
    staff_id BIGINT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    reason NVARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL CONSTRAINT DF_Staff_Leaves_Status DEFAULT ('PENDING'),
    created_at DATETIME2(0) NOT NULL CONSTRAINT DF_Staff_Leaves_Created DEFAULT (SYSDATETIME()),
    reviewed_by BIGINT NULL,
    reviewed_at DATETIME2(0) NULL,
    CONSTRAINT PK_Staff_Leaves PRIMARY KEY (leave_id),
    CONSTRAINT CK_Staff_Leaves_Dates CHECK (start_date <= end_date),
    CONSTRAINT CK_Staff_Leaves_Status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT CK_Staff_Leaves_Review CHECK (
        (status = 'PENDING' AND reviewed_by IS NULL AND reviewed_at IS NULL)
        OR (status IN ('APPROVED', 'REJECTED') AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)
    ),
    CONSTRAINT FK_Staff_Leaves_Staff FOREIGN KEY (staff_id) REFERENCES dbo.Staff(staff_id) ON DELETE NO ACTION,
    CONSTRAINT FK_Staff_Leaves_Reviewer FOREIGN KEY (reviewed_by) REFERENCES dbo.Users(user_id) ON DELETE NO ACTION
);
CREATE INDEX IX_Staff_Leaves_Dates ON dbo.Staff_Leaves(staff_id, start_date, end_date, status);

CREATE TABLE dbo.Bookings (
    booking_id BIGINT IDENTITY(1,1) NOT NULL,
    customer_id BIGINT NOT NULL,
    staff_id BIGINT NOT NULL,
    original_staff_id BIGINT NOT NULL,
    house_id BIGINT NOT NULL,
    address_snapshot NVARCHAR(500) NOT NULL,
    latitude_snapshot DECIMAL(10,7) NOT NULL,
    longitude_snapshot DECIMAL(10,7) NOT NULL,
    address_note_snapshot NVARCHAR(500) NULL,
    start_time DATETIME2(0) NOT NULL,
    end_time DATETIME2(0) NOT NULL,
    total_duration_minutes INT NOT NULL,
    total_amount DECIMAL(12,2) NOT NULL,
    area_note NVARCHAR(MAX) NULL,
    status VARCHAR(20) NOT NULL CONSTRAINT DF_Bookings_Status DEFAULT ('CONFIRMED'),
    cancelled_at DATETIME2(0) NULL,
    cancelled_by BIGINT NULL,
    cancellation_reason NVARCHAR(500) NULL,
    created_at DATETIME2(0) NOT NULL CONSTRAINT DF_Bookings_Created DEFAULT (SYSDATETIME()),
    CONSTRAINT PK_Bookings PRIMARY KEY (booking_id),
    CONSTRAINT CK_Bookings_Time CHECK (start_time < end_time),
    CONSTRAINT CK_Bookings_Duration CHECK (total_duration_minutes > 0),
    CONSTRAINT CK_Bookings_Amount CHECK (total_amount >= 0),
    CONSTRAINT CK_Bookings_Latitude CHECK (latitude_snapshot BETWEEN -90 AND 90),
    CONSTRAINT CK_Bookings_Longitude CHECK (longitude_snapshot BETWEEN -180 AND 180),
    CONSTRAINT CK_Bookings_Status CHECK (status IN ('CONFIRMED', 'AWAITING_PAYMENT', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT CK_Bookings_Cancellation CHECK (
        (status = 'CANCELLED' AND cancelled_at IS NOT NULL AND cancelled_by IS NOT NULL AND cancellation_reason IS NOT NULL)
        OR (status <> 'CANCELLED' AND cancelled_at IS NULL AND cancelled_by IS NULL AND cancellation_reason IS NULL)
    ),
    CONSTRAINT FK_Bookings_Customer FOREIGN KEY (customer_id) REFERENCES dbo.Users(user_id) ON DELETE NO ACTION,
    CONSTRAINT FK_Bookings_Staff FOREIGN KEY (staff_id) REFERENCES dbo.Staff(staff_id) ON DELETE NO ACTION,
    CONSTRAINT FK_Bookings_Original_Staff FOREIGN KEY (original_staff_id) REFERENCES dbo.Staff(staff_id) ON DELETE NO ACTION,
    CONSTRAINT FK_Bookings_House FOREIGN KEY (house_id) REFERENCES dbo.Houses(house_id) ON DELETE NO ACTION,
    CONSTRAINT FK_Bookings_Cancelled_By FOREIGN KEY (cancelled_by) REFERENCES dbo.Users(user_id) ON DELETE NO ACTION
);
CREATE INDEX IX_Bookings_Staff_Time_Status ON dbo.Bookings(staff_id, start_time, end_time, status);
CREATE INDEX IX_Bookings_Customer_Created ON dbo.Bookings(customer_id, created_at DESC);
CREATE INDEX IX_Bookings_House_Status ON dbo.Bookings(house_id, status);

CREATE TABLE dbo.Booking_Details (
    booking_detail_id BIGINT IDENTITY(1,1) NOT NULL,
    booking_id BIGINT NOT NULL,
    service_id BIGINT NOT NULL,
    service_name_snapshot NVARCHAR(150) NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    duration_minutes INT NOT NULL,
    CONSTRAINT PK_Booking_Details PRIMARY KEY (booking_detail_id),
    CONSTRAINT CK_Booking_Details_Price CHECK (unit_price >= 0),
    CONSTRAINT CK_Booking_Details_Duration CHECK (duration_minutes > 0),
    CONSTRAINT FK_Booking_Details_Booking FOREIGN KEY (booking_id) REFERENCES dbo.Bookings(booking_id) ON DELETE NO ACTION,
    CONSTRAINT FK_Booking_Details_Service FOREIGN KEY (service_id) REFERENCES dbo.Services(service_id) ON DELETE NO ACTION
);
CREATE INDEX IX_Booking_Details_Booking ON dbo.Booking_Details(booking_id);

CREATE TABLE dbo.Booking_Images (
    image_id BIGINT IDENTITY(1,1) NOT NULL,
    booking_id BIGINT NOT NULL,
    image_url NVARCHAR(500) NOT NULL,
    uploaded_at DATETIME2(0) NOT NULL CONSTRAINT DF_Booking_Images_Uploaded DEFAULT (SYSDATETIME()),
    CONSTRAINT PK_Booking_Images PRIMARY KEY (image_id),
    CONSTRAINT FK_Booking_Images_Booking FOREIGN KEY (booking_id) REFERENCES dbo.Bookings(booking_id) ON DELETE NO ACTION
);
CREATE INDEX IX_Booking_Images_Booking ON dbo.Booking_Images(booking_id, uploaded_at);

CREATE TABLE dbo.Payments (
    payment_id BIGINT IDENTITY(1,1) NOT NULL,
    booking_id BIGINT NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    payment_method VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL CONSTRAINT DF_Payments_Status DEFAULT ('PENDING'),
    transaction_code NVARCHAR(255) NULL,
    created_at DATETIME2(0) NOT NULL CONSTRAINT DF_Payments_Created DEFAULT (SYSDATETIME()),
    paid_at DATETIME2(0) NULL,
    CONSTRAINT PK_Payments PRIMARY KEY (payment_id),
    CONSTRAINT CK_Payments_Amount CHECK (amount >= 0),
    CONSTRAINT CK_Payments_Method CHECK (payment_method IN ('CASH', 'ONLINE')),
    CONSTRAINT CK_Payments_Status CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED')),
    CONSTRAINT CK_Payments_Paid_At CHECK (
        (status = 'SUCCESS' AND paid_at IS NOT NULL) OR (status <> 'SUCCESS' AND paid_at IS NULL)
    ),
    CONSTRAINT FK_Payments_Booking FOREIGN KEY (booking_id) REFERENCES dbo.Bookings(booking_id) ON DELETE NO ACTION
);
CREATE INDEX IX_Payments_Booking_Status ON dbo.Payments(booking_id, status, created_at DESC);
CREATE UNIQUE INDEX UX_Payments_One_Success_Per_Booking ON dbo.Payments(booking_id) WHERE status = 'SUCCESS';

CREATE TABLE dbo.Feedbacks (
    feedback_id BIGINT IDENTITY(1,1) NOT NULL,
    booking_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    content NVARCHAR(MAX) NOT NULL,
    admin_reply NVARCHAR(MAX) NULL,
    status VARCHAR(20) NOT NULL CONSTRAINT DF_Feedbacks_Status DEFAULT ('OPEN'),
    CONSTRAINT PK_Feedbacks PRIMARY KEY (feedback_id),
    CONSTRAINT UQ_Feedbacks_Booking UNIQUE (booking_id),
    CONSTRAINT CK_Feedbacks_Status CHECK (status IN ('OPEN', 'RESOLVED')),
    CONSTRAINT FK_Feedbacks_Booking FOREIGN KEY (booking_id) REFERENCES dbo.Bookings(booking_id) ON DELETE NO ACTION,
    CONSTRAINT FK_Feedbacks_Customer FOREIGN KEY (customer_id) REFERENCES dbo.Users(user_id) ON DELETE NO ACTION
);

CREATE TABLE dbo.Staff_Follows (
    customer_id BIGINT NOT NULL,
    staff_id BIGINT NOT NULL,
    created_at DATETIME2(0) NOT NULL CONSTRAINT DF_Staff_Follows_Created DEFAULT (SYSDATETIME()),
    CONSTRAINT PK_Staff_Follows PRIMARY KEY (customer_id, staff_id),
    CONSTRAINT FK_Staff_Follows_Customer FOREIGN KEY (customer_id) REFERENCES dbo.Users(user_id) ON DELETE NO ACTION,
    CONSTRAINT FK_Staff_Follows_Staff FOREIGN KEY (staff_id) REFERENCES dbo.Staff(staff_id) ON DELETE NO ACTION
);
CREATE INDEX IX_Staff_Follows_Staff ON dbo.Staff_Follows(staff_id, created_at DESC);

-- Technical sequence for unique integer PayOS orderCode values (not a business table).
CREATE SEQUENCE dbo.PayOS_Order_Code_Seq
    AS BIGINT START WITH 100000000 INCREMENT BY 1 MINVALUE 100000000 MAXVALUE 2147483647 NO CYCLE;
