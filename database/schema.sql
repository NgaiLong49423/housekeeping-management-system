-- Housekeeping Management System Database Schema
-- Target DBMS: Microsoft SQL Server
-- Total tables: 25 business tables matching docs/requirements/SQL_SERVER_DATA_DICTIONARY.md

SET NOCOUNT ON;
SET XACT_ABORT ON;
GO
SET ANSI_NULLS ON
GO

SET QUOTED_IDENTIFIER ON
GO

CREATE TABLE [dbo].[Users](
	[user_id] [bigint] IDENTITY(1,1) NOT NULL,
	[full_name] [nvarchar](150) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
	[email] [nvarchar](255) COLLATE Latin1_General_100_CI_AS NOT NULL,
	[password_hash] [nvarchar](255) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
	[phone] [nvarchar](20) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
	[role] [varchar](20) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
	[status] [varchar](20) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
 CONSTRAINT [PK_Users] PRIMARY KEY CLUSTERED
(
	[user_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY],
 CONSTRAINT [UQ_Users_Email] UNIQUE NONCLUSTERED
(
	[email] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]

GO

SET ANSI_NULLS ON
GO

SET QUOTED_IDENTIFIER ON
GO

CREATE TABLE [dbo].[Company_Settings](
	[company_id] [bigint] IDENTITY(1,1) NOT NULL,
	[company_address] [nvarchar](500) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
	[latitude] [decimal](10, 7) NOT NULL,
	[longitude] [decimal](10, 7) NOT NULL,
	[service_radius_km] [decimal](6, 2) NOT NULL,
 CONSTRAINT [PK_Company_Settings] PRIMARY KEY CLUSTERED
(
	[company_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]

GO

SET ANSI_NULLS ON
GO

SET QUOTED_IDENTIFIER ON
GO

CREATE TABLE [dbo].[Services](
	[service_id] [bigint] IDENTITY(1,1) NOT NULL,
	[service_name] [nvarchar](150) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
	[description] [nvarchar](max) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
	[image_url] [nvarchar](500) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
	[price] [decimal](12, 2) NOT NULL,
	[duration_minutes] [int] NOT NULL,
	[status] [varchar](20) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
 CONSTRAINT [PK_Services] PRIMARY KEY CLUSTERED
(
	[service_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]

GO

SET ANSI_NULLS ON
GO

SET QUOTED_IDENTIFIER ON
GO

CREATE TABLE [dbo].[Booking_Delay_Compensation_Tiers](
	[tier_id] [int] IDENTITY(1,1) NOT NULL,
	[min_delay_minutes] [int] NOT NULL,
	[max_delay_minutes] [int] NULL,
	[discount_percent] [int] NOT NULL,
 CONSTRAINT [PK_Booking_Delay_Compensation_Tiers] PRIMARY KEY CLUSTERED
(
	[tier_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY],
 CONSTRAINT [UQ_Booking_Delay_Compensation_Tiers_Min] UNIQUE NONCLUSTERED
(
	[min_delay_minutes] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]

GO

SET ANSI_NULLS ON
GO

SET QUOTED_IDENTIFIER ON
GO

CREATE TABLE [dbo].[Staff](
	[staff_id] [bigint] IDENTITY(1,1) NOT NULL,
	[user_id] [bigint] NOT NULL,
	[staff_type] [varchar](20) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
 CONSTRAINT [PK_Staff] PRIMARY KEY CLUSTERED
(
	[staff_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY],
 CONSTRAINT [UQ_Staff_User] UNIQUE NONCLUSTERED
(
	[user_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]

GO

SET ANSI_NULLS ON
GO

SET QUOTED_IDENTIFIER ON
GO

CREATE TABLE [dbo].[Houses](
	[house_id] [bigint] IDENTITY(1,1) NOT NULL,
	[customer_id] [bigint] NOT NULL,
	[house_name] [nvarchar](100) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
	[address] [nvarchar](500) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
	[latitude] [decimal](10, 7) NULL,
	[longitude] [decimal](10, 7) NULL,
	[address_note] [nvarchar](500) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
	[is_deleted] [bit] NOT NULL,
	[recipient_name] [nvarchar](150) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
	[recipient_phone] [nvarchar](20) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
	[province_city] [nvarchar](100) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
	[ward_commune] [nvarchar](100) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
	[address_detail] [nvarchar](500) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
	[address_type] [varchar](20) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
	[is_default] [bit] NOT NULL,
 CONSTRAINT [PK_Houses] PRIMARY KEY CLUSTERED
(
	[house_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]

GO

SET ANSI_NULLS ON
GO

SET QUOTED_IDENTIFIER ON
GO

CREATE TABLE [dbo].[Company_Operating_Hours](
	[operating_hour_id] [bigint] IDENTITY(1,1) NOT NULL,
	[company_id] [bigint] NOT NULL,
	[day_of_week] [int] NOT NULL,
	[opening_time] [time](0) NULL,
	[closing_time] [time](0) NULL,
	[is_closed] [bit] NOT NULL,
	[period_no] [smallint] NOT NULL,
	[effective_from] [date] NOT NULL,
 CONSTRAINT [PK_Company_Operating_Hours] PRIMARY KEY CLUSTERED
(
	[operating_hour_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]

GO

SET ANSI_NULLS ON
GO

SET QUOTED_IDENTIFIER ON
GO

CREATE TABLE [dbo].[Bookings](
	[booking_id] [bigint] IDENTITY(1,1) NOT NULL,
	[customer_id] [bigint] NOT NULL,
	[staff_id] [bigint] NOT NULL,
	[original_staff_id] [bigint] NOT NULL,
	[house_id] [bigint] NOT NULL,
	[address_snapshot] [nvarchar](500) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
	[latitude_snapshot] [decimal](10, 7) NULL,
	[longitude_snapshot] [decimal](10, 7) NULL,
	[address_note_snapshot] [nvarchar](500) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
	[start_time] [datetime2](0) NOT NULL,
	[end_time] [datetime2](0) NOT NULL,
	[total_duration_minutes] [int] NOT NULL,
	[total_amount] [decimal](12, 2) NOT NULL,
	[area_note] [nvarchar](max) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
	[status] [varchar](20) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
	[cancelled_at] [datetime2](0) NULL,
	[cancelled_by] [bigint] NULL,
	[cancellation_reason] [nvarchar](500) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
	[created_at] [datetime2](0) NOT NULL,
	[customer_arrived_at] [datetime2](0) NULL,
	[staff_arrived_at] [datetime2](0) NULL,
	[incident_reported_at] [datetime2](0) NULL,
	[incident_note] [nvarchar](500) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
	[work_started_at] [datetime2](0) NULL,
	[incident_expected_arrival_at] [datetime2](0) NULL,
	[incident_customer_response] [varchar](10) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
	[incident_customer_responded_at] [datetime2](0) NULL,
	[delay_minutes] [int] NULL,
	[delay_discount_percent] [decimal](5, 2) NOT NULL,
	[delay_discount_amount] [decimal](12, 2) NOT NULL,
	[customer_delay_reported_at] [datetime2](0) NULL,
	[service_completed_at] [datetime2](0) NULL,
	[arrival_verified_at] [datetime2](7) NULL,
	[arrival_verification_recorded_at] [datetime2](7) NULL,
	[arrival_verified_by] [bigint] NULL,
	[arrival_verification_source] [varchar](10) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
	[arrival_verification_note] [nvarchar](500) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
 CONSTRAINT [PK_Bookings] PRIMARY KEY CLUSTERED
(
	[booking_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]

GO

SET ANSI_NULLS ON
GO

SET QUOTED_IDENTIFIER ON
GO

CREATE TABLE [dbo].[Staff_Profiles](
	[profile_id] [bigint] IDENTITY(1,1) NOT NULL,
	[staff_id] [bigint] NOT NULL,
	[avatar_url] [nvarchar](500) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
	[bio] [nvarchar](max) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
 CONSTRAINT [PK_Staff_Profiles] PRIMARY KEY CLUSTERED
(
	[profile_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY],
 CONSTRAINT [UQ_Staff_Profiles_Staff] UNIQUE NONCLUSTERED
(
	[staff_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]

GO

SET ANSI_NULLS ON
GO

SET QUOTED_IDENTIFIER ON
GO

CREATE TABLE [dbo].[Staff_Leaves](
	[leave_id] [bigint] IDENTITY(1,1) NOT NULL,
	[staff_id] [bigint] NOT NULL,
	[start_date] [date] NOT NULL,
	[end_date] [date] NOT NULL,
	[reason] [nvarchar](500) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
	[status] [varchar](20) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
	[created_at] [datetime2](0) NOT NULL,
	[reviewed_by] [bigint] NULL,
	[reviewed_at] [datetime2](0) NULL,
 CONSTRAINT [PK_Staff_Leaves] PRIMARY KEY CLUSTERED
(
	[leave_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]

GO

SET ANSI_NULLS ON
GO

SET QUOTED_IDENTIFIER ON
GO

CREATE TABLE [dbo].[Staff_Follows](
	[customer_id] [bigint] NOT NULL,
	[staff_id] [bigint] NOT NULL,
	[created_at] [datetime2](0) NOT NULL,
 CONSTRAINT [PK_Staff_Follows] PRIMARY KEY CLUSTERED
(
	[customer_id] ASC,
	[staff_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]

GO

SET ANSI_NULLS ON
GO

SET QUOTED_IDENTIFIER ON
GO

CREATE TABLE [dbo].[Staff_Availability](
	[availability_id] [bigint] IDENTITY(1,1) NOT NULL,
	[staff_id] [bigint] NOT NULL,
	[work_date] [date] NOT NULL,
	[start_time] [time](0) NOT NULL,
	[end_time] [time](0) NOT NULL,
	[created_at] [datetime2](0) NOT NULL,
	[is_active] [bit] NOT NULL,
 CONSTRAINT [PK_Staff_Availability] PRIMARY KEY CLUSTERED
(
	[availability_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]

GO

SET ANSI_NULLS ON
GO

SET QUOTED_IDENTIFIER ON
GO

CREATE TABLE [dbo].[Booking_Details](
	[booking_detail_id] [bigint] IDENTITY(1,1) NOT NULL,
	[booking_id] [bigint] NOT NULL,
	[service_id] [bigint] NOT NULL,
	[service_name_snapshot] [nvarchar](150) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
	[unit_price] [decimal](12, 2) NOT NULL,
	[duration_minutes] [int] NOT NULL,
 CONSTRAINT [PK_Booking_Details] PRIMARY KEY CLUSTERED
(
	[booking_detail_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]

GO

SET ANSI_NULLS ON
GO

SET QUOTED_IDENTIFIER ON
GO

CREATE TABLE [dbo].[Payments](
	[payment_id] [bigint] IDENTITY(1,1) NOT NULL,
	[booking_id] [bigint] NOT NULL,
	[amount] [decimal](12, 2) NOT NULL,
	[payment_method] [varchar](20) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
	[status] [varchar](20) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
	[transaction_code] [nvarchar](255) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
	[created_at] [datetime2](0) NOT NULL,
	[paid_at] [datetime2](0) NULL,
	[is_simulated] [bit] NOT NULL,
 CONSTRAINT [PK_Payments] PRIMARY KEY CLUSTERED
(
	[payment_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]

GO

SET ANSI_NULLS ON
GO

SET QUOTED_IDENTIFIER ON
GO

CREATE TABLE [dbo].[Booking_Images](
	[image_id] [bigint] IDENTITY(1,1) NOT NULL,
	[booking_id] [bigint] NOT NULL,
	[image_url] [nvarchar](500) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
	[uploaded_at] [datetime2](0) NOT NULL,
 CONSTRAINT [PK_Booking_Images] PRIMARY KEY CLUSTERED
(
	[image_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]

GO

SET ANSI_NULLS ON
GO

SET QUOTED_IDENTIFIER ON
GO

CREATE TABLE [dbo].[Feedbacks](
	[feedback_id] [bigint] IDENTITY(1,1) NOT NULL,
	[booking_id] [bigint] NOT NULL,
	[customer_id] [bigint] NOT NULL,
	[content] [nvarchar](max) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
	[admin_reply] [nvarchar](max) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
	[status] [varchar](20) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
	[rating] [tinyint] NULL,
	[topics] [nvarchar](300) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
 CONSTRAINT [PK_Feedbacks] PRIMARY KEY CLUSTERED
(
	[feedback_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY],
 CONSTRAINT [UQ_Feedbacks_Booking] UNIQUE NONCLUSTERED
(
	[booking_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]

GO

SET ANSI_NULLS ON
GO

SET QUOTED_IDENTIFIER ON
GO

CREATE TABLE [dbo].[Feedback_Cases](
	[case_id] [bigint] IDENTITY(1,1) NOT NULL,
	[feedback_id] [bigint] NOT NULL,
	[incident_types] [nvarchar](150) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
	[customer_description] [nvarchar](max) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
	[case_status] [varchar](40) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
	[customer_outcome] [nvarchar](max) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
	[remedy_type] [varchar](30) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
	[remedy_completed] [bit] NOT NULL,
	[internal_finding] [nvarchar](max) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
	[staff_action] [varchar](30) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
	[staff_action_status] [varchar](30) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
	[staff_action_by] [bigint] NULL,
	[staff_action_approved_by] [bigint] NULL,
	[reviewed_by] [bigint] NULL,
	[reviewed_at] [datetime2](0) NULL,
	[created_at] [datetime2](0) NOT NULL,
	[resolved_at] [datetime2](0) NULL,
	[item_description] [nvarchar](300) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
	[customer_estimated_value] [decimal](12, 2) NULL,
	[item_age_months] [int] NULL,
	[customer_requested_resolution] [nvarchar](1000) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
 CONSTRAINT [PK_Feedback_Cases] PRIMARY KEY CLUSTERED
(
	[case_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY],
 CONSTRAINT [UQ_Feedback_Cases_Feedback] UNIQUE NONCLUSTERED
(
	[feedback_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]

GO

SET ANSI_NULLS ON
GO

SET QUOTED_IDENTIFIER ON
GO

CREATE TABLE [dbo].[Booking_Financial_Entries](
	[entry_id] [bigint] IDENTITY(1,1) NOT NULL,
	[booking_id] [bigint] NOT NULL,
	[case_id] [bigint] NULL,
	[entry_type] [varchar](30) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
	[amount] [decimal](12, 2) NOT NULL,
	[currency] [char](3) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
	[reason] [nvarchar](1000) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
	[status] [varchar](20) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
	[payment_method] [varchar](20) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
	[transaction_reference] [nvarchar](255) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
	[receipt_path] [nvarchar](500) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
	[is_simulated] [bit] NOT NULL,
	[created_by] [bigint] NOT NULL,
	[approved_by] [bigint] NULL,
	[settled_by] [bigint] NULL,
	[created_at] [datetime2](0) NOT NULL,
	[approved_at] [datetime2](0) NULL,
	[paid_at] [datetime2](0) NULL,
	[cancelled_by] [bigint] NULL,
	[cancellation_reason] [nvarchar](1000) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
	[rejection_reason] [nvarchar](1000) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
 CONSTRAINT [PK_Booking_Financial_Entries] PRIMARY KEY CLUSTERED
(
	[entry_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]

GO

SET ANSI_NULLS ON
GO

SET QUOTED_IDENTIFIER ON
GO

CREATE TABLE [dbo].[Feedback_Case_Verifications](
	[verification_id] [bigint] IDENTITY(1,1) NOT NULL,
	[case_id] [bigint] NOT NULL,
	[admin_id] [bigint] NOT NULL,
	[case_status] [varchar](30) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
	[reviewed_evidence_ids] [nvarchar](2000) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
	[internal_finding] [nvarchar](4000) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
	[created_at] [datetime2](0) NOT NULL,
 CONSTRAINT [PK_Feedback_Case_Verifications] PRIMARY KEY CLUSTERED
(
	[verification_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]

GO

SET ANSI_NULLS ON
GO

SET QUOTED_IDENTIFIER ON
GO

CREATE TABLE [dbo].[Feedback_Case_Messages](
	[message_id] [bigint] IDENTITY(1,1) NOT NULL,
	[case_id] [bigint] NOT NULL,
	[sender_user_id] [bigint] NULL,
	[sender_role] [varchar](20) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
	[message_text] [nvarchar](max) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
	[sent_at] [datetime2](0) NOT NULL,
	[legacy_imported] [bit] NOT NULL,
	[legacy_timestamp_unknown] [bit] NOT NULL,
 CONSTRAINT [PK_Feedback_Case_Messages] PRIMARY KEY CLUSTERED
(
	[message_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]

GO

SET ANSI_NULLS ON
GO

SET QUOTED_IDENTIFIER ON
GO

CREATE TABLE [dbo].[Staff_Incident_Actions](
	[action_id] [bigint] IDENTITY(1,1) NOT NULL,
	[case_id] [bigint] NOT NULL,
	[staff_id] [bigint] NOT NULL,
	[action_type] [varchar](30) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
	[status] [varchar](30) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
	[reason] [nvarchar](1000) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
	[staff_notice] [nvarchar](1000) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
	[proposed_by] [bigint] NOT NULL,
	[decided_by] [bigint] NULL,
	[created_at] [datetime2](0) NOT NULL,
	[decided_at] [datetime2](0) NULL,
	[acknowledged_at] [datetime2](0) NULL,
	[response_deadline] [datetime2](0) NULL,
	[staff_response_expired_at] [datetime2](0) NULL,
	[staff_response] [nvarchar](4000) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
	[staff_responded_at] [datetime2](0) NULL,
	[appeal_text] [nvarchar](4000) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
	[appealed_at] [datetime2](0) NULL,
	[decision_reason] [nvarchar](1000) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
	[liability_amount] [decimal](12, 2) NULL,
	[finalized_at] [datetime2](0) NULL,
	[appeal_decided_by] [bigint] NULL,
	[appeal_decided_at] [datetime2](0) NULL,
	[decision_outcome] [varchar](20) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
 CONSTRAINT [PK_Staff_Incident_Actions] PRIMARY KEY CLUSTERED
(
	[action_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]

GO

SET ANSI_NULLS ON
GO

SET QUOTED_IDENTIFIER ON
GO

CREATE TABLE [dbo].[Staff_Recovery_Installments](
	[installment_id] [bigint] IDENTITY(1,1) NOT NULL,
	[action_id] [bigint] NOT NULL,
	[amount] [decimal](12, 2) NOT NULL,
	[payment_method] [varchar](20) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
	[transaction_reference] [nvarchar](255) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
	[receipt_path] [nvarchar](500) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
	[received_by] [bigint] NOT NULL,
	[received_at] [datetime2](0) NOT NULL,
 CONSTRAINT [PK_Staff_Recovery_Installments] PRIMARY KEY CLUSTERED
(
	[installment_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]

GO

SET ANSI_NULLS ON
GO

SET QUOTED_IDENTIFIER ON
GO

CREATE TABLE [dbo].[Staff_Incident_Action_Evidence](
	[evidence_id] [bigint] IDENTITY(1,1) NOT NULL,
	[action_id] [bigint] NOT NULL,
	[uploaded_by] [bigint] NOT NULL,
	[evidence_stage] [varchar](20) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
	[media_type] [varchar](20) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
	[content_type] [varchar](100) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
	[original_name] [nvarchar](255) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
	[storage_path] [nvarchar](500) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
	[uploaded_at] [datetime2](0) NOT NULL,
 CONSTRAINT [PK_Staff_Incident_Action_Evidence] PRIMARY KEY CLUSTERED
(
	[evidence_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]

GO

SET ANSI_NULLS ON
GO

SET QUOTED_IDENTIFIER ON
GO

CREATE TABLE [dbo].[Financial_Payout_Responses](
	[payout_response_id] [bigint] IDENTITY(1,1) NOT NULL,
	[entry_id] [bigint] NOT NULL,
	[customer_id] [bigint] NOT NULL,
	[response_status] [varchar](24) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
	[payout_method] [varchar](20) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
	[encrypted_account_details] [nvarchar](max) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
	[responded_at] [datetime2](0) NOT NULL,
	[customer_confirmed_at] [datetime2](0) NULL,
	[amount_agreed_at] [datetime2](0) NULL,
	[payout_method_selected_at] [datetime2](0) NULL,
 CONSTRAINT [PK_Financial_Payout_Responses] PRIMARY KEY CLUSTERED
(
	[payout_response_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY],
 CONSTRAINT [UQ_Financial_Payout_Responses_Entry] UNIQUE NONCLUSTERED
(
	[entry_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]

GO

SET ANSI_NULLS ON
GO

SET QUOTED_IDENTIFIER ON
GO

CREATE TABLE [dbo].[Feedback_Case_Evidence](
	[evidence_id] [bigint] IDENTITY(1,1) NOT NULL,
	[case_id] [bigint] NOT NULL,
	[image_path] [nvarchar](500) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
	[uploaded_by] [bigint] NOT NULL,
	[uploaded_at] [datetime2](0) NOT NULL,
	[message_id] [bigint] NULL,
	[media_type] [varchar](20) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
	[content_type] [varchar](100) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
	[original_name] [nvarchar](255) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
	[external_url] [nvarchar](1000) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
 CONSTRAINT [PK_Feedback_Case_Evidence] PRIMARY KEY CLUSTERED
(
	[evidence_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]

GO

SET ANSI_PADDING ON

GO

CREATE UNIQUE NONCLUSTERED INDEX [UX_Users_Single_Manager] ON [dbo].[Users]
(
	[role] ASC
)
WHERE ([role]='MANAGER')
WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO

CREATE UNIQUE NONCLUSTERED INDEX [UX_Booking_Delay_Compensation_Tiers_OpenEnded] ON [dbo].[Booking_Delay_Compensation_Tiers]
(
	[max_delay_minutes] ASC
)
WHERE ([max_delay_minutes] IS NULL)
WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO

CREATE NONCLUSTERED INDEX [IX_Houses_Customer_Deleted] ON [dbo].[Houses]
(
	[customer_id] ASC,
	[is_deleted] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO

CREATE UNIQUE NONCLUSTERED INDEX [UX_Houses_One_Default_Per_Customer] ON [dbo].[Houses]
(
	[customer_id] ASC
)
WHERE ([is_default]=(1) AND [is_deleted]=(0))
WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO

CREATE NONCLUSTERED INDEX [IX_Company_Operating_Hours_Effective_From] ON [dbo].[Company_Operating_Hours]
(
	[company_id] ASC,
	[effective_from] DESC,
	[day_of_week] ASC,
	[period_no] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO

CREATE UNIQUE NONCLUSTERED INDEX [UX_Company_Operating_Hours_Company_Effective_Day_Period] ON [dbo].[Company_Operating_Hours]
(
	[company_id] ASC,
	[effective_from] ASC,
	[day_of_week] ASC,
	[period_no] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO

CREATE NONCLUSTERED INDEX [IX_Bookings_Customer_Created] ON [dbo].[Bookings]
(
	[customer_id] ASC,
	[created_at] DESC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO

SET ANSI_PADDING ON

GO

CREATE NONCLUSTERED INDEX [IX_Bookings_House_Status] ON [dbo].[Bookings]
(
	[house_id] ASC,
	[status] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO

SET ANSI_PADDING ON

GO

CREATE NONCLUSTERED INDEX [IX_Bookings_Staff_Time_Status] ON [dbo].[Bookings]
(
	[staff_id] ASC,
	[start_time] ASC,
	[end_time] ASC,
	[status] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO

SET ANSI_PADDING ON

GO

CREATE NONCLUSTERED INDEX [IX_Staff_Leaves_Dates] ON [dbo].[Staff_Leaves]
(
	[staff_id] ASC,
	[start_date] ASC,
	[end_date] ASC,
	[status] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO

CREATE NONCLUSTERED INDEX [IX_Staff_Follows_Staff] ON [dbo].[Staff_Follows]
(
	[staff_id] ASC,
	[created_at] DESC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO

CREATE NONCLUSTERED INDEX [IX_Staff_Availability_Date] ON [dbo].[Staff_Availability]
(
	[staff_id] ASC,
	[work_date] ASC,
	[start_time] ASC,
	[end_time] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO

CREATE NONCLUSTERED INDEX [IX_Booking_Details_Booking] ON [dbo].[Booking_Details]
(
	[booking_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO

SET ANSI_PADDING ON

GO

CREATE NONCLUSTERED INDEX [IX_Payments_Booking_Status] ON [dbo].[Payments]
(
	[booking_id] ASC,
	[status] ASC,
	[created_at] DESC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO

CREATE UNIQUE NONCLUSTERED INDEX [UX_Payments_One_Success_Per_Booking] ON [dbo].[Payments]
(
	[booking_id] ASC
)
WHERE ([status]='SUCCESS')
WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO

CREATE NONCLUSTERED INDEX [IX_Booking_Images_Booking] ON [dbo].[Booking_Images]
(
	[booking_id] ASC,
	[uploaded_at] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO

SET ANSI_PADDING ON

GO

CREATE NONCLUSTERED INDEX [IX_Feedback_Cases_Status] ON [dbo].[Feedback_Cases]
(
	[case_status] ASC,
	[created_at] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO

SET ANSI_PADDING ON

GO

CREATE NONCLUSTERED INDEX [IX_Booking_Financial_Entries_Booking] ON [dbo].[Booking_Financial_Entries]
(
	[booking_id] ASC,
	[status] ASC,
	[entry_type] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO

CREATE NONCLUSTERED INDEX [IX_Feedback_Case_Verifications_Case_Date] ON [dbo].[Feedback_Case_Verifications]
(
	[case_id] ASC,
	[created_at] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO

CREATE NONCLUSTERED INDEX [IX_Feedback_Case_Messages_CaseTime] ON [dbo].[Feedback_Case_Messages]
(
	[case_id] ASC,
	[sent_at] ASC,
	[message_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO

ALTER TABLE [dbo].[Users] ADD  CONSTRAINT [DF_Users_Status]  DEFAULT ('ACTIVE') FOR [status]
GO

ALTER TABLE [dbo].[Services] ADD  CONSTRAINT [DF_Services_Status]  DEFAULT ('ACTIVE') FOR [status]
GO

ALTER TABLE [dbo].[Houses] ADD  CONSTRAINT [DF_Houses_Is_Deleted]  DEFAULT ((0)) FOR [is_deleted]
GO

ALTER TABLE [dbo].[Houses] ADD  CONSTRAINT [DF_Houses_Address_Type]  DEFAULT ('HOME') FOR [address_type]
GO

ALTER TABLE [dbo].[Houses] ADD  CONSTRAINT [DF_Houses_Is_Default]  DEFAULT ((0)) FOR [is_default]
GO

ALTER TABLE [dbo].[Company_Operating_Hours] ADD  CONSTRAINT [DF_Company_Operating_Hours_Closed]  DEFAULT ((0)) FOR [is_closed]
GO

ALTER TABLE [dbo].[Company_Operating_Hours] ADD  CONSTRAINT [DF_Company_Operating_Hours_Period]  DEFAULT ((1)) FOR [period_no]
GO

ALTER TABLE [dbo].[Company_Operating_Hours] ADD  CONSTRAINT [DF_Company_Operating_Hours_Effective_From]  DEFAULT ('19000101') FOR [effective_from]
GO

ALTER TABLE [dbo].[Bookings] ADD  CONSTRAINT [DF_Bookings_Status]  DEFAULT ('CONFIRMED') FOR [status]
GO

ALTER TABLE [dbo].[Bookings] ADD  CONSTRAINT [DF_Bookings_Created]  DEFAULT (sysdatetime()) FOR [created_at]
GO

ALTER TABLE [dbo].[Bookings] ADD  CONSTRAINT [DF_Bookings_Delay_Discount_Percent]  DEFAULT ((0)) FOR [delay_discount_percent]
GO

ALTER TABLE [dbo].[Bookings] ADD  CONSTRAINT [DF_Bookings_Delay_Discount_Amount]  DEFAULT ((0)) FOR [delay_discount_amount]
GO

ALTER TABLE [dbo].[Staff_Leaves] ADD  CONSTRAINT [DF_Staff_Leaves_Status]  DEFAULT ('PENDING') FOR [status]
GO

ALTER TABLE [dbo].[Staff_Leaves] ADD  CONSTRAINT [DF_Staff_Leaves_Created]  DEFAULT (sysdatetime()) FOR [created_at]
GO

ALTER TABLE [dbo].[Staff_Follows] ADD  CONSTRAINT [DF_Staff_Follows_Created]  DEFAULT (sysdatetime()) FOR [created_at]
GO

ALTER TABLE [dbo].[Staff_Availability] ADD  CONSTRAINT [DF_Staff_Availability_Created]  DEFAULT (sysdatetime()) FOR [created_at]
GO

ALTER TABLE [dbo].[Staff_Availability] ADD  CONSTRAINT [DF_Staff_Availability_Active]  DEFAULT ((1)) FOR [is_active]
GO

ALTER TABLE [dbo].[Payments] ADD  CONSTRAINT [DF_Payments_Status]  DEFAULT ('PENDING') FOR [status]
GO

ALTER TABLE [dbo].[Payments] ADD  CONSTRAINT [DF_Payments_Created]  DEFAULT (sysdatetime()) FOR [created_at]
GO

ALTER TABLE [dbo].[Payments] ADD  CONSTRAINT [DF_Payments_IsSimulated]  DEFAULT ((0)) FOR [is_simulated]
GO

ALTER TABLE [dbo].[Booking_Images] ADD  CONSTRAINT [DF_Booking_Images_Uploaded]  DEFAULT (sysdatetime()) FOR [uploaded_at]
GO

ALTER TABLE [dbo].[Feedbacks] ADD  CONSTRAINT [DF_Feedbacks_Status]  DEFAULT ('OPEN') FOR [status]
GO

ALTER TABLE [dbo].[Feedbacks] ADD  CONSTRAINT [DF_Feedbacks_Topics]  DEFAULT (N'') FOR [topics]
GO

ALTER TABLE [dbo].[Feedback_Cases] ADD  CONSTRAINT [DF_Feedback_Cases_Status]  DEFAULT ('RECEIVED') FOR [case_status]
GO

ALTER TABLE [dbo].[Feedback_Cases] ADD  CONSTRAINT [DF_Feedback_Cases_RemedyCompleted]  DEFAULT ((0)) FOR [remedy_completed]
GO

ALTER TABLE [dbo].[Feedback_Cases] ADD  CONSTRAINT [DF_Feedback_Cases_StaffActionStatus]  DEFAULT ('NOT_PROPOSED') FOR [staff_action_status]
GO

ALTER TABLE [dbo].[Feedback_Cases] ADD  CONSTRAINT [DF_Feedback_Cases_Created]  DEFAULT (sysdatetime()) FOR [created_at]
GO

ALTER TABLE [dbo].[Booking_Financial_Entries] ADD  CONSTRAINT [DF_Booking_Financial_Entries_Currency]  DEFAULT ('VND') FOR [currency]
GO

ALTER TABLE [dbo].[Booking_Financial_Entries] ADD  CONSTRAINT [DF_Booking_Financial_Entries_Status]  DEFAULT ('PROPOSED') FOR [status]
GO

ALTER TABLE [dbo].[Booking_Financial_Entries] ADD  CONSTRAINT [DF_Booking_Financial_Entries_Simulated]  DEFAULT ((0)) FOR [is_simulated]
GO

ALTER TABLE [dbo].[Booking_Financial_Entries] ADD  CONSTRAINT [DF_Booking_Financial_Entries_Created]  DEFAULT (sysdatetime()) FOR [created_at]
GO

ALTER TABLE [dbo].[Feedback_Case_Verifications] ADD  CONSTRAINT [DF_Feedback_Case_Verifications_Created]  DEFAULT (sysdatetime()) FOR [created_at]
GO

ALTER TABLE [dbo].[Feedback_Case_Messages] ADD  CONSTRAINT [DF_Feedback_Case_Messages_Sent]  DEFAULT (sysdatetime()) FOR [sent_at]
GO

ALTER TABLE [dbo].[Feedback_Case_Messages] ADD  CONSTRAINT [DF_Feedback_Case_Messages_Legacy]  DEFAULT ((0)) FOR [legacy_imported]
GO

ALTER TABLE [dbo].[Feedback_Case_Messages] ADD  CONSTRAINT [DF_Feedback_Case_Messages_LegacyTime]  DEFAULT ((0)) FOR [legacy_timestamp_unknown]
GO

ALTER TABLE [dbo].[Staff_Incident_Actions] ADD  CONSTRAINT [DF_Staff_Incident_Actions_Status]  DEFAULT ('PENDING') FOR [status]
GO

ALTER TABLE [dbo].[Staff_Incident_Actions] ADD  CONSTRAINT [DF_Staff_Incident_Actions_Created]  DEFAULT (sysdatetime()) FOR [created_at]
GO

ALTER TABLE [dbo].[Staff_Recovery_Installments] ADD  CONSTRAINT [DF_Staff_Recovery_Installments_Received]  DEFAULT (sysdatetime()) FOR [received_at]
GO

ALTER TABLE [dbo].[Staff_Incident_Action_Evidence] ADD  CONSTRAINT [DF_Staff_Action_Evidence_Uploaded]  DEFAULT (sysdatetime()) FOR [uploaded_at]
GO

ALTER TABLE [dbo].[Financial_Payout_Responses] ADD  CONSTRAINT [DF_Financial_Payout_Responses_Responded]  DEFAULT (sysdatetime()) FOR [responded_at]
GO

ALTER TABLE [dbo].[Feedback_Case_Evidence] ADD  CONSTRAINT [DF_Feedback_Case_Evidence_Uploaded]  DEFAULT (sysdatetime()) FOR [uploaded_at]
GO

ALTER TABLE [dbo].[Feedback_Case_Evidence] ADD  CONSTRAINT [DF_Feedback_Case_Evidence_Media_Type]  DEFAULT ('IMAGE') FOR [media_type]
GO

ALTER TABLE [dbo].[Staff]  WITH CHECK ADD  CONSTRAINT [FK_Staff_User] FOREIGN KEY([user_id])
REFERENCES [dbo].[Users] ([user_id])
GO

ALTER TABLE [dbo].[Staff] CHECK CONSTRAINT [FK_Staff_User]
GO

ALTER TABLE [dbo].[Houses]  WITH CHECK ADD  CONSTRAINT [FK_Houses_Customer] FOREIGN KEY([customer_id])
REFERENCES [dbo].[Users] ([user_id])
GO

ALTER TABLE [dbo].[Houses] CHECK CONSTRAINT [FK_Houses_Customer]
GO

ALTER TABLE [dbo].[Company_Operating_Hours]  WITH CHECK ADD  CONSTRAINT [FK_Company_Operating_Hours_Company] FOREIGN KEY([company_id])
REFERENCES [dbo].[Company_Settings] ([company_id])
GO

ALTER TABLE [dbo].[Company_Operating_Hours] CHECK CONSTRAINT [FK_Company_Operating_Hours_Company]
GO

ALTER TABLE [dbo].[Bookings]  WITH CHECK ADD  CONSTRAINT [FK_Bookings_ArrivalVerifiedBy_Users] FOREIGN KEY([arrival_verified_by])
REFERENCES [dbo].[Users] ([user_id])
GO

ALTER TABLE [dbo].[Bookings] CHECK CONSTRAINT [FK_Bookings_ArrivalVerifiedBy_Users]
GO

ALTER TABLE [dbo].[Bookings]  WITH CHECK ADD  CONSTRAINT [FK_Bookings_Cancelled_By] FOREIGN KEY([cancelled_by])
REFERENCES [dbo].[Users] ([user_id])
GO

ALTER TABLE [dbo].[Bookings] CHECK CONSTRAINT [FK_Bookings_Cancelled_By]
GO

ALTER TABLE [dbo].[Bookings]  WITH CHECK ADD  CONSTRAINT [FK_Bookings_Customer] FOREIGN KEY([customer_id])
REFERENCES [dbo].[Users] ([user_id])
GO

ALTER TABLE [dbo].[Bookings] CHECK CONSTRAINT [FK_Bookings_Customer]
GO

ALTER TABLE [dbo].[Bookings]  WITH CHECK ADD  CONSTRAINT [FK_Bookings_House] FOREIGN KEY([house_id])
REFERENCES [dbo].[Houses] ([house_id])
GO

ALTER TABLE [dbo].[Bookings] CHECK CONSTRAINT [FK_Bookings_House]
GO

ALTER TABLE [dbo].[Bookings]  WITH CHECK ADD  CONSTRAINT [FK_Bookings_Original_Staff] FOREIGN KEY([original_staff_id])
REFERENCES [dbo].[Staff] ([staff_id])
GO

ALTER TABLE [dbo].[Bookings] CHECK CONSTRAINT [FK_Bookings_Original_Staff]
GO

ALTER TABLE [dbo].[Bookings]  WITH CHECK ADD  CONSTRAINT [FK_Bookings_Staff] FOREIGN KEY([staff_id])
REFERENCES [dbo].[Staff] ([staff_id])
GO

ALTER TABLE [dbo].[Bookings] CHECK CONSTRAINT [FK_Bookings_Staff]
GO

ALTER TABLE [dbo].[Staff_Profiles]  WITH CHECK ADD  CONSTRAINT [FK_Staff_Profiles_Staff] FOREIGN KEY([staff_id])
REFERENCES [dbo].[Staff] ([staff_id])
GO

ALTER TABLE [dbo].[Staff_Profiles] CHECK CONSTRAINT [FK_Staff_Profiles_Staff]
GO

ALTER TABLE [dbo].[Staff_Leaves]  WITH CHECK ADD  CONSTRAINT [FK_Staff_Leaves_Reviewer] FOREIGN KEY([reviewed_by])
REFERENCES [dbo].[Users] ([user_id])
GO

ALTER TABLE [dbo].[Staff_Leaves] CHECK CONSTRAINT [FK_Staff_Leaves_Reviewer]
GO

ALTER TABLE [dbo].[Staff_Leaves]  WITH CHECK ADD  CONSTRAINT [FK_Staff_Leaves_Staff] FOREIGN KEY([staff_id])
REFERENCES [dbo].[Staff] ([staff_id])
GO

ALTER TABLE [dbo].[Staff_Leaves] CHECK CONSTRAINT [FK_Staff_Leaves_Staff]
GO

ALTER TABLE [dbo].[Staff_Follows]  WITH CHECK ADD  CONSTRAINT [FK_Staff_Follows_Customer] FOREIGN KEY([customer_id])
REFERENCES [dbo].[Users] ([user_id])
GO

ALTER TABLE [dbo].[Staff_Follows] CHECK CONSTRAINT [FK_Staff_Follows_Customer]
GO

ALTER TABLE [dbo].[Staff_Follows]  WITH CHECK ADD  CONSTRAINT [FK_Staff_Follows_Staff] FOREIGN KEY([staff_id])
REFERENCES [dbo].[Staff] ([staff_id])
GO

ALTER TABLE [dbo].[Staff_Follows] CHECK CONSTRAINT [FK_Staff_Follows_Staff]
GO

ALTER TABLE [dbo].[Staff_Availability]  WITH CHECK ADD  CONSTRAINT [FK_Staff_Availability_Staff] FOREIGN KEY([staff_id])
REFERENCES [dbo].[Staff] ([staff_id])
GO

ALTER TABLE [dbo].[Staff_Availability] CHECK CONSTRAINT [FK_Staff_Availability_Staff]
GO

ALTER TABLE [dbo].[Booking_Details]  WITH CHECK ADD  CONSTRAINT [FK_Booking_Details_Booking] FOREIGN KEY([booking_id])
REFERENCES [dbo].[Bookings] ([booking_id])
GO

ALTER TABLE [dbo].[Booking_Details] CHECK CONSTRAINT [FK_Booking_Details_Booking]
GO

ALTER TABLE [dbo].[Booking_Details]  WITH CHECK ADD  CONSTRAINT [FK_Booking_Details_Service] FOREIGN KEY([service_id])
REFERENCES [dbo].[Services] ([service_id])
GO

ALTER TABLE [dbo].[Booking_Details] CHECK CONSTRAINT [FK_Booking_Details_Service]
GO

ALTER TABLE [dbo].[Payments]  WITH CHECK ADD  CONSTRAINT [FK_Payments_Booking] FOREIGN KEY([booking_id])
REFERENCES [dbo].[Bookings] ([booking_id])
GO

ALTER TABLE [dbo].[Payments] CHECK CONSTRAINT [FK_Payments_Booking]
GO

ALTER TABLE [dbo].[Booking_Images]  WITH CHECK ADD  CONSTRAINT [FK_Booking_Images_Booking] FOREIGN KEY([booking_id])
REFERENCES [dbo].[Bookings] ([booking_id])
GO

ALTER TABLE [dbo].[Booking_Images] CHECK CONSTRAINT [FK_Booking_Images_Booking]
GO

ALTER TABLE [dbo].[Feedbacks]  WITH CHECK ADD  CONSTRAINT [FK_Feedbacks_Booking] FOREIGN KEY([booking_id])
REFERENCES [dbo].[Bookings] ([booking_id])
GO

ALTER TABLE [dbo].[Feedbacks] CHECK CONSTRAINT [FK_Feedbacks_Booking]
GO

ALTER TABLE [dbo].[Feedbacks]  WITH CHECK ADD  CONSTRAINT [FK_Feedbacks_Customer] FOREIGN KEY([customer_id])
REFERENCES [dbo].[Users] ([user_id])
GO

ALTER TABLE [dbo].[Feedbacks] CHECK CONSTRAINT [FK_Feedbacks_Customer]
GO

ALTER TABLE [dbo].[Feedback_Cases]  WITH CHECK ADD  CONSTRAINT [FK_Feedback_Cases_Feedback] FOREIGN KEY([feedback_id])
REFERENCES [dbo].[Feedbacks] ([feedback_id])
GO

ALTER TABLE [dbo].[Feedback_Cases] CHECK CONSTRAINT [FK_Feedback_Cases_Feedback]
GO

ALTER TABLE [dbo].[Feedback_Cases]  WITH CHECK ADD  CONSTRAINT [FK_Feedback_Cases_Reviewer] FOREIGN KEY([reviewed_by])
REFERENCES [dbo].[Users] ([user_id])
GO

ALTER TABLE [dbo].[Feedback_Cases] CHECK CONSTRAINT [FK_Feedback_Cases_Reviewer]
GO

ALTER TABLE [dbo].[Feedback_Cases]  WITH CHECK ADD  CONSTRAINT [FK_Feedback_Cases_StaffActionApprovedBy] FOREIGN KEY([staff_action_approved_by])
REFERENCES [dbo].[Users] ([user_id])
GO

ALTER TABLE [dbo].[Feedback_Cases] CHECK CONSTRAINT [FK_Feedback_Cases_StaffActionApprovedBy]
GO

ALTER TABLE [dbo].[Feedback_Cases]  WITH CHECK ADD  CONSTRAINT [FK_Feedback_Cases_StaffActionBy] FOREIGN KEY([staff_action_by])
REFERENCES [dbo].[Users] ([user_id])
GO

ALTER TABLE [dbo].[Feedback_Cases] CHECK CONSTRAINT [FK_Feedback_Cases_StaffActionBy]
GO

ALTER TABLE [dbo].[Booking_Financial_Entries]  WITH CHECK ADD  CONSTRAINT [FK_Booking_Financial_Entries_Approver] FOREIGN KEY([approved_by])
REFERENCES [dbo].[Users] ([user_id])
GO

ALTER TABLE [dbo].[Booking_Financial_Entries] CHECK CONSTRAINT [FK_Booking_Financial_Entries_Approver]
GO

ALTER TABLE [dbo].[Booking_Financial_Entries]  WITH CHECK ADD  CONSTRAINT [FK_Booking_Financial_Entries_Booking] FOREIGN KEY([booking_id])
REFERENCES [dbo].[Bookings] ([booking_id])
GO

ALTER TABLE [dbo].[Booking_Financial_Entries] CHECK CONSTRAINT [FK_Booking_Financial_Entries_Booking]
GO

ALTER TABLE [dbo].[Booking_Financial_Entries]  WITH CHECK ADD  CONSTRAINT [FK_Booking_Financial_Entries_CancelledBy] FOREIGN KEY([cancelled_by])
REFERENCES [dbo].[Users] ([user_id])
GO

ALTER TABLE [dbo].[Booking_Financial_Entries] CHECK CONSTRAINT [FK_Booking_Financial_Entries_CancelledBy]
GO

ALTER TABLE [dbo].[Booking_Financial_Entries]  WITH CHECK ADD  CONSTRAINT [FK_Booking_Financial_Entries_Case] FOREIGN KEY([case_id])
REFERENCES [dbo].[Feedback_Cases] ([case_id])
GO

ALTER TABLE [dbo].[Booking_Financial_Entries] CHECK CONSTRAINT [FK_Booking_Financial_Entries_Case]
GO

ALTER TABLE [dbo].[Booking_Financial_Entries]  WITH CHECK ADD  CONSTRAINT [FK_Booking_Financial_Entries_Creator] FOREIGN KEY([created_by])
REFERENCES [dbo].[Users] ([user_id])
GO

ALTER TABLE [dbo].[Booking_Financial_Entries] CHECK CONSTRAINT [FK_Booking_Financial_Entries_Creator]
GO

ALTER TABLE [dbo].[Booking_Financial_Entries]  WITH CHECK ADD  CONSTRAINT [FK_Booking_Financial_Entries_Settler] FOREIGN KEY([settled_by])
REFERENCES [dbo].[Users] ([user_id])
GO

ALTER TABLE [dbo].[Booking_Financial_Entries] CHECK CONSTRAINT [FK_Booking_Financial_Entries_Settler]
GO

ALTER TABLE [dbo].[Feedback_Case_Verifications]  WITH CHECK ADD  CONSTRAINT [FK_Feedback_Case_Verifications_Admin] FOREIGN KEY([admin_id])
REFERENCES [dbo].[Users] ([user_id])
GO

ALTER TABLE [dbo].[Feedback_Case_Verifications] CHECK CONSTRAINT [FK_Feedback_Case_Verifications_Admin]
GO

ALTER TABLE [dbo].[Feedback_Case_Verifications]  WITH CHECK ADD  CONSTRAINT [FK_Feedback_Case_Verifications_Case] FOREIGN KEY([case_id])
REFERENCES [dbo].[Feedback_Cases] ([case_id])
GO

ALTER TABLE [dbo].[Feedback_Case_Verifications] CHECK CONSTRAINT [FK_Feedback_Case_Verifications_Case]
GO

ALTER TABLE [dbo].[Feedback_Case_Messages]  WITH CHECK ADD  CONSTRAINT [FK_Feedback_Case_Messages_Case] FOREIGN KEY([case_id])
REFERENCES [dbo].[Feedback_Cases] ([case_id])
GO

ALTER TABLE [dbo].[Feedback_Case_Messages] CHECK CONSTRAINT [FK_Feedback_Case_Messages_Case]
GO

ALTER TABLE [dbo].[Feedback_Case_Messages]  WITH CHECK ADD  CONSTRAINT [FK_Feedback_Case_Messages_Sender] FOREIGN KEY([sender_user_id])
REFERENCES [dbo].[Users] ([user_id])
GO

ALTER TABLE [dbo].[Feedback_Case_Messages] CHECK CONSTRAINT [FK_Feedback_Case_Messages_Sender]
GO

ALTER TABLE [dbo].[Staff_Incident_Actions]  WITH CHECK ADD  CONSTRAINT [FK_Staff_Incident_Actions_AppealManager] FOREIGN KEY([appeal_decided_by])
REFERENCES [dbo].[Users] ([user_id])
GO

ALTER TABLE [dbo].[Staff_Incident_Actions] CHECK CONSTRAINT [FK_Staff_Incident_Actions_AppealManager]
GO

ALTER TABLE [dbo].[Staff_Incident_Actions]  WITH CHECK ADD  CONSTRAINT [FK_Staff_Incident_Actions_Case] FOREIGN KEY([case_id])
REFERENCES [dbo].[Feedback_Cases] ([case_id])
GO

ALTER TABLE [dbo].[Staff_Incident_Actions] CHECK CONSTRAINT [FK_Staff_Incident_Actions_Case]
GO

ALTER TABLE [dbo].[Staff_Incident_Actions]  WITH CHECK ADD  CONSTRAINT [FK_Staff_Incident_Actions_Decider] FOREIGN KEY([decided_by])
REFERENCES [dbo].[Users] ([user_id])
GO

ALTER TABLE [dbo].[Staff_Incident_Actions] CHECK CONSTRAINT [FK_Staff_Incident_Actions_Decider]
GO

ALTER TABLE [dbo].[Staff_Incident_Actions]  WITH CHECK ADD  CONSTRAINT [FK_Staff_Incident_Actions_Proposer] FOREIGN KEY([proposed_by])
REFERENCES [dbo].[Users] ([user_id])
GO

ALTER TABLE [dbo].[Staff_Incident_Actions] CHECK CONSTRAINT [FK_Staff_Incident_Actions_Proposer]
GO

ALTER TABLE [dbo].[Staff_Incident_Actions]  WITH CHECK ADD  CONSTRAINT [FK_Staff_Incident_Actions_Staff] FOREIGN KEY([staff_id])
REFERENCES [dbo].[Staff] ([staff_id])
GO

ALTER TABLE [dbo].[Staff_Incident_Actions] CHECK CONSTRAINT [FK_Staff_Incident_Actions_Staff]
GO

ALTER TABLE [dbo].[Staff_Recovery_Installments]  WITH CHECK ADD  CONSTRAINT [FK_Staff_Recovery_Installments_Action] FOREIGN KEY([action_id])
REFERENCES [dbo].[Staff_Incident_Actions] ([action_id])
GO

ALTER TABLE [dbo].[Staff_Recovery_Installments] CHECK CONSTRAINT [FK_Staff_Recovery_Installments_Action]
GO

ALTER TABLE [dbo].[Staff_Recovery_Installments]  WITH CHECK ADD  CONSTRAINT [FK_Staff_Recovery_Installments_User] FOREIGN KEY([received_by])
REFERENCES [dbo].[Users] ([user_id])
GO

ALTER TABLE [dbo].[Staff_Recovery_Installments] CHECK CONSTRAINT [FK_Staff_Recovery_Installments_User]
GO

ALTER TABLE [dbo].[Staff_Incident_Action_Evidence]  WITH CHECK ADD  CONSTRAINT [FK_Staff_Action_Evidence_Action] FOREIGN KEY([action_id])
REFERENCES [dbo].[Staff_Incident_Actions] ([action_id])
GO

ALTER TABLE [dbo].[Staff_Incident_Action_Evidence] CHECK CONSTRAINT [FK_Staff_Action_Evidence_Action]
GO

ALTER TABLE [dbo].[Staff_Incident_Action_Evidence]  WITH CHECK ADD  CONSTRAINT [FK_Staff_Action_Evidence_User] FOREIGN KEY([uploaded_by])
REFERENCES [dbo].[Users] ([user_id])
GO

ALTER TABLE [dbo].[Staff_Incident_Action_Evidence] CHECK CONSTRAINT [FK_Staff_Action_Evidence_User]
GO

ALTER TABLE [dbo].[Financial_Payout_Responses]  WITH CHECK ADD  CONSTRAINT [FK_Financial_Payout_Responses_Customer] FOREIGN KEY([customer_id])
REFERENCES [dbo].[Users] ([user_id])
GO

ALTER TABLE [dbo].[Financial_Payout_Responses] CHECK CONSTRAINT [FK_Financial_Payout_Responses_Customer]
GO

ALTER TABLE [dbo].[Financial_Payout_Responses]  WITH CHECK ADD  CONSTRAINT [FK_Financial_Payout_Responses_Entry] FOREIGN KEY([entry_id])
REFERENCES [dbo].[Booking_Financial_Entries] ([entry_id])
GO

ALTER TABLE [dbo].[Financial_Payout_Responses] CHECK CONSTRAINT [FK_Financial_Payout_Responses_Entry]
GO

ALTER TABLE [dbo].[Feedback_Case_Evidence]  WITH CHECK ADD  CONSTRAINT [FK_Feedback_Case_Evidence_Case] FOREIGN KEY([case_id])
REFERENCES [dbo].[Feedback_Cases] ([case_id])
GO

ALTER TABLE [dbo].[Feedback_Case_Evidence] CHECK CONSTRAINT [FK_Feedback_Case_Evidence_Case]
GO

ALTER TABLE [dbo].[Feedback_Case_Evidence]  WITH CHECK ADD  CONSTRAINT [FK_Feedback_Case_Evidence_Message] FOREIGN KEY([message_id])
REFERENCES [dbo].[Feedback_Case_Messages] ([message_id])
GO

ALTER TABLE [dbo].[Feedback_Case_Evidence] CHECK CONSTRAINT [FK_Feedback_Case_Evidence_Message]
GO

ALTER TABLE [dbo].[Feedback_Case_Evidence]  WITH CHECK ADD  CONSTRAINT [FK_Feedback_Case_Evidence_User] FOREIGN KEY([uploaded_by])
REFERENCES [dbo].[Users] ([user_id])
GO

ALTER TABLE [dbo].[Feedback_Case_Evidence] CHECK CONSTRAINT [FK_Feedback_Case_Evidence_User]
GO

ALTER TABLE [dbo].[Users]  WITH CHECK ADD  CONSTRAINT [CK_Users_Role] CHECK  (([role]='MANAGER' OR [role]='ADMIN' OR [role]='STAFF' OR [role]='CUSTOMER'))
GO

ALTER TABLE [dbo].[Users] CHECK CONSTRAINT [CK_Users_Role]
GO

ALTER TABLE [dbo].[Users]  WITH CHECK ADD  CONSTRAINT [CK_Users_Status] CHECK  (([status]='INACTIVE' OR [status]='ACTIVE'))
GO

ALTER TABLE [dbo].[Users] CHECK CONSTRAINT [CK_Users_Status]
GO

ALTER TABLE [dbo].[Company_Settings]  WITH CHECK ADD  CONSTRAINT [CK_Company_Settings_Latitude] CHECK  (([latitude]>=(-90) AND [latitude]<=(90)))
GO

ALTER TABLE [dbo].[Company_Settings] CHECK CONSTRAINT [CK_Company_Settings_Latitude]
GO

ALTER TABLE [dbo].[Company_Settings]  WITH CHECK ADD  CONSTRAINT [CK_Company_Settings_Longitude] CHECK  (([longitude]>=(-180) AND [longitude]<=(180)))
GO

ALTER TABLE [dbo].[Company_Settings] CHECK CONSTRAINT [CK_Company_Settings_Longitude]
GO

ALTER TABLE [dbo].[Company_Settings]  WITH CHECK ADD  CONSTRAINT [CK_Company_Settings_Radius] CHECK  (([service_radius_km]>(0)))
GO

ALTER TABLE [dbo].[Company_Settings] CHECK CONSTRAINT [CK_Company_Settings_Radius]
GO

ALTER TABLE [dbo].[Services]  WITH CHECK ADD  CONSTRAINT [CK_Services_Duration] CHECK  (([duration_minutes]>(0)))
GO

ALTER TABLE [dbo].[Services] CHECK CONSTRAINT [CK_Services_Duration]
GO

ALTER TABLE [dbo].[Services]  WITH CHECK ADD  CONSTRAINT [CK_Services_Price] CHECK  (([price]>=(0)))
GO

ALTER TABLE [dbo].[Services] CHECK CONSTRAINT [CK_Services_Price]
GO

ALTER TABLE [dbo].[Services]  WITH CHECK ADD  CONSTRAINT [CK_Services_Status] CHECK  (([status]='INACTIVE' OR [status]='ACTIVE'))
GO

ALTER TABLE [dbo].[Services] CHECK CONSTRAINT [CK_Services_Status]
GO

ALTER TABLE [dbo].[Booking_Delay_Compensation_Tiers]  WITH CHECK ADD  CONSTRAINT [CK_Booking_Delay_Compensation_Tiers_Percent] CHECK  (([discount_percent]>=(0) AND [discount_percent]<=(100)))
GO

ALTER TABLE [dbo].[Booking_Delay_Compensation_Tiers] CHECK CONSTRAINT [CK_Booking_Delay_Compensation_Tiers_Percent]
GO

ALTER TABLE [dbo].[Booking_Delay_Compensation_Tiers]  WITH CHECK ADD  CONSTRAINT [CK_Booking_Delay_Compensation_Tiers_Range] CHECK  (([min_delay_minutes]>=(1) AND ([max_delay_minutes] IS NULL OR [max_delay_minutes]>=[min_delay_minutes])))
GO

ALTER TABLE [dbo].[Booking_Delay_Compensation_Tiers] CHECK CONSTRAINT [CK_Booking_Delay_Compensation_Tiers_Range]
GO

ALTER TABLE [dbo].[Staff]  WITH CHECK ADD  CONSTRAINT [CK_Staff_Type] CHECK  (([staff_type]='PART_TIME' OR [staff_type]='FULL_TIME'))
GO

ALTER TABLE [dbo].[Staff] CHECK CONSTRAINT [CK_Staff_Type]
GO

ALTER TABLE [dbo].[Houses]  WITH CHECK ADD  CONSTRAINT [CK_Houses_Address_Type] CHECK  (([address_type]='OFFICE' OR [address_type]='HOME'))
GO

ALTER TABLE [dbo].[Houses] CHECK CONSTRAINT [CK_Houses_Address_Type]
GO

ALTER TABLE [dbo].[Houses]  WITH CHECK ADD  CONSTRAINT [CK_Houses_Latitude] CHECK  (([latitude]>=(-90) AND [latitude]<=(90)))
GO

ALTER TABLE [dbo].[Houses] CHECK CONSTRAINT [CK_Houses_Latitude]
GO

ALTER TABLE [dbo].[Houses]  WITH CHECK ADD  CONSTRAINT [CK_Houses_Longitude] CHECK  (([longitude]>=(-180) AND [longitude]<=(180)))
GO

ALTER TABLE [dbo].[Houses] CHECK CONSTRAINT [CK_Houses_Longitude]
GO

ALTER TABLE [dbo].[Company_Operating_Hours]  WITH CHECK ADD  CONSTRAINT [CK_Company_Operating_Hours_Day] CHECK  (([day_of_week]>=(1) AND [day_of_week]<=(7)))
GO

ALTER TABLE [dbo].[Company_Operating_Hours] CHECK CONSTRAINT [CK_Company_Operating_Hours_Day]
GO

ALTER TABLE [dbo].[Company_Operating_Hours]  WITH CHECK ADD  CONSTRAINT [CK_Company_Operating_Hours_Times] CHECK  (([is_closed]=(1) AND [opening_time] IS NULL AND [closing_time] IS NULL OR [is_closed]=(0) AND [opening_time] IS NOT NULL AND [closing_time] IS NOT NULL AND [opening_time]<[closing_time]))
GO

ALTER TABLE [dbo].[Company_Operating_Hours] CHECK CONSTRAINT [CK_Company_Operating_Hours_Times]
GO

ALTER TABLE [dbo].[Bookings]  WITH CHECK ADD  CONSTRAINT [CK_Bookings_Amount] CHECK  (([total_amount]>=(0)))
GO

ALTER TABLE [dbo].[Bookings] CHECK CONSTRAINT [CK_Bookings_Amount]
GO

ALTER TABLE [dbo].[Bookings]  WITH CHECK ADD  CONSTRAINT [CK_Bookings_ArrivalVerificationAudit] CHECK  (([arrival_verified_at] IS NULL AND [arrival_verification_recorded_at] IS NULL AND [arrival_verified_by] IS NULL AND [arrival_verification_source] IS NULL AND [arrival_verification_note] IS NULL OR [arrival_verified_at] IS NOT NULL AND [arrival_verification_recorded_at] IS NOT NULL AND [arrival_verified_by] IS NOT NULL AND [arrival_verification_source] IS NOT NULL))
GO

ALTER TABLE [dbo].[Bookings] CHECK CONSTRAINT [CK_Bookings_ArrivalVerificationAudit]
GO

ALTER TABLE [dbo].[Bookings]  WITH CHECK ADD  CONSTRAINT [CK_Bookings_ArrivalVerificationSource] CHECK  (([arrival_verification_source] IS NULL OR ([arrival_verification_source]='ADMIN' OR [arrival_verification_source]='CUSTOMER')))
GO

ALTER TABLE [dbo].[Bookings] CHECK CONSTRAINT [CK_Bookings_ArrivalVerificationSource]
GO

ALTER TABLE [dbo].[Bookings]  WITH CHECK ADD  CONSTRAINT [CK_Bookings_Cancellation] CHECK  (([status]='CANCELLED' AND [cancelled_at] IS NOT NULL AND [cancelled_by] IS NOT NULL AND [cancellation_reason] IS NOT NULL OR [status]<>'CANCELLED' AND [cancelled_at] IS NULL AND [cancelled_by] IS NULL AND [cancellation_reason] IS NULL))
GO

ALTER TABLE [dbo].[Bookings] CHECK CONSTRAINT [CK_Bookings_Cancellation]
GO

ALTER TABLE [dbo].[Bookings]  WITH CHECK ADD  CONSTRAINT [CK_Bookings_Customer_Delay_Report] CHECK  (([customer_delay_reported_at] IS NULL OR [customer_delay_reported_at]>=[start_time]))
GO

ALTER TABLE [dbo].[Bookings] CHECK CONSTRAINT [CK_Bookings_Customer_Delay_Report]
GO

ALTER TABLE [dbo].[Bookings]  WITH CHECK ADD  CONSTRAINT [CK_Bookings_Delay_Discount_Amount] CHECK  (([delay_discount_amount]>=(0) AND [delay_discount_amount]<=[total_amount]))
GO

ALTER TABLE [dbo].[Bookings] CHECK CONSTRAINT [CK_Bookings_Delay_Discount_Amount]
GO

ALTER TABLE [dbo].[Bookings]  WITH CHECK ADD  CONSTRAINT [CK_Bookings_Delay_Discount_Percent] CHECK  (([delay_discount_percent]>=(0) AND [delay_discount_percent]<=(100)))
GO

ALTER TABLE [dbo].[Bookings] CHECK CONSTRAINT [CK_Bookings_Delay_Discount_Percent]
GO

ALTER TABLE [dbo].[Bookings]  WITH CHECK ADD  CONSTRAINT [CK_Bookings_Delay_Minutes] CHECK  (([delay_minutes] IS NULL OR [delay_minutes]>=(0)))
GO

ALTER TABLE [dbo].[Bookings] CHECK CONSTRAINT [CK_Bookings_Delay_Minutes]
GO

ALTER TABLE [dbo].[Bookings]  WITH CHECK ADD  CONSTRAINT [CK_Bookings_Duration] CHECK  (([total_duration_minutes]>(0)))
GO

ALTER TABLE [dbo].[Bookings] CHECK CONSTRAINT [CK_Bookings_Duration]
GO

ALTER TABLE [dbo].[Bookings]  WITH CHECK ADD  CONSTRAINT [CK_Bookings_Incident_Customer_Response] CHECK  (([incident_customer_response] IS NULL AND [incident_customer_responded_at] IS NULL OR ([incident_customer_response]='DECLINED' OR [incident_customer_response]='ACCEPTED') AND [incident_customer_responded_at] IS NOT NULL))
GO

ALTER TABLE [dbo].[Bookings] CHECK CONSTRAINT [CK_Bookings_Incident_Customer_Response]
GO

ALTER TABLE [dbo].[Bookings]  WITH CHECK ADD  CONSTRAINT [CK_Bookings_Incident_Report] CHECK  (([incident_reported_at] IS NULL AND [incident_note] IS NULL OR [incident_reported_at] IS NOT NULL AND [incident_note] IS NOT NULL AND len(ltrim(rtrim([incident_note])))>(0)))
GO

ALTER TABLE [dbo].[Bookings] CHECK CONSTRAINT [CK_Bookings_Incident_Report]
GO

ALTER TABLE [dbo].[Bookings]  WITH CHECK ADD  CONSTRAINT [CK_Bookings_Incident_Response_Requires_Report] CHECK  (([incident_customer_response] IS NULL OR [incident_reported_at] IS NOT NULL AND [incident_note] IS NOT NULL))
GO

ALTER TABLE [dbo].[Bookings] CHECK CONSTRAINT [CK_Bookings_Incident_Response_Requires_Report]
GO

ALTER TABLE [dbo].[Bookings]  WITH CHECK ADD  CONSTRAINT [CK_Bookings_Latitude] CHECK  (([latitude_snapshot]>=(-90) AND [latitude_snapshot]<=(90)))
GO

ALTER TABLE [dbo].[Bookings] CHECK CONSTRAINT [CK_Bookings_Latitude]
GO

ALTER TABLE [dbo].[Bookings]  WITH CHECK ADD  CONSTRAINT [CK_Bookings_Longitude] CHECK  (([longitude_snapshot]>=(-180) AND [longitude_snapshot]<=(180)))
GO

ALTER TABLE [dbo].[Bookings] CHECK CONSTRAINT [CK_Bookings_Longitude]
GO

ALTER TABLE [dbo].[Bookings]  WITH CHECK ADD  CONSTRAINT [CK_Bookings_Status] CHECK  (([status]='CANCELLED' OR [status]='COMPLETED' OR [status]='AWAITING_PAYMENT' OR [status]='CONFIRMED'))
GO

ALTER TABLE [dbo].[Bookings] CHECK CONSTRAINT [CK_Bookings_Status]
GO

ALTER TABLE [dbo].[Bookings]  WITH CHECK ADD  CONSTRAINT [CK_Bookings_Time] CHECK  (([start_time]<[end_time]))
GO

ALTER TABLE [dbo].[Bookings] CHECK CONSTRAINT [CK_Bookings_Time]
GO

ALTER TABLE [dbo].[Bookings]  WITH CHECK ADD  CONSTRAINT [CK_Bookings_Work_Start] CHECK  (([work_started_at] IS NULL OR [staff_arrived_at] IS NOT NULL))
GO

ALTER TABLE [dbo].[Bookings] CHECK CONSTRAINT [CK_Bookings_Work_Start]
GO

ALTER TABLE [dbo].[Staff_Leaves]  WITH CHECK ADD  CONSTRAINT [CK_Staff_Leaves_Dates] CHECK  (([start_date]<=[end_date]))
GO

ALTER TABLE [dbo].[Staff_Leaves] CHECK CONSTRAINT [CK_Staff_Leaves_Dates]
GO

ALTER TABLE [dbo].[Staff_Leaves]  WITH CHECK ADD  CONSTRAINT [CK_Staff_Leaves_Review] CHECK  (([status]='PENDING' AND [reviewed_by] IS NULL AND [reviewed_at] IS NULL OR ([status]='REJECTED' OR [status]='APPROVED') AND [reviewed_by] IS NOT NULL AND [reviewed_at] IS NOT NULL))
GO

ALTER TABLE [dbo].[Staff_Leaves] CHECK CONSTRAINT [CK_Staff_Leaves_Review]
GO

ALTER TABLE [dbo].[Staff_Leaves]  WITH CHECK ADD  CONSTRAINT [CK_Staff_Leaves_Status] CHECK  (([status]='REJECTED' OR [status]='APPROVED' OR [status]='PENDING'))
GO

ALTER TABLE [dbo].[Staff_Leaves] CHECK CONSTRAINT [CK_Staff_Leaves_Status]
GO

ALTER TABLE [dbo].[Staff_Availability]  WITH CHECK ADD  CONSTRAINT [CK_Staff_Availability_Time] CHECK  (([start_time]<[end_time]))
GO

ALTER TABLE [dbo].[Staff_Availability] CHECK CONSTRAINT [CK_Staff_Availability_Time]
GO

ALTER TABLE [dbo].[Booking_Details]  WITH CHECK ADD  CONSTRAINT [CK_Booking_Details_Duration] CHECK  (([duration_minutes]>(0)))
GO

ALTER TABLE [dbo].[Booking_Details] CHECK CONSTRAINT [CK_Booking_Details_Duration]
GO

ALTER TABLE [dbo].[Booking_Details]  WITH CHECK ADD  CONSTRAINT [CK_Booking_Details_Price] CHECK  (([unit_price]>=(0)))
GO

ALTER TABLE [dbo].[Booking_Details] CHECK CONSTRAINT [CK_Booking_Details_Price]
GO

ALTER TABLE [dbo].[Payments]  WITH CHECK ADD  CONSTRAINT [CK_Payments_Amount] CHECK  (([amount]>=(0)))
GO

ALTER TABLE [dbo].[Payments] CHECK CONSTRAINT [CK_Payments_Amount]
GO

ALTER TABLE [dbo].[Payments]  WITH CHECK ADD  CONSTRAINT [CK_Payments_Method] CHECK  (([payment_method]='ONLINE' OR [payment_method]='CASH'))
GO

ALTER TABLE [dbo].[Payments] CHECK CONSTRAINT [CK_Payments_Method]
GO

ALTER TABLE [dbo].[Payments]  WITH CHECK ADD  CONSTRAINT [CK_Payments_Paid_At] CHECK  (([status]='SUCCESS' AND [paid_at] IS NOT NULL OR [status]<>'SUCCESS' AND [paid_at] IS NULL))
GO

ALTER TABLE [dbo].[Payments] CHECK CONSTRAINT [CK_Payments_Paid_At]
GO

ALTER TABLE [dbo].[Payments]  WITH CHECK ADD  CONSTRAINT [CK_Payments_Status] CHECK  (([status]='FAILED' OR [status]='SUCCESS' OR [status]='PENDING'))
GO

ALTER TABLE [dbo].[Payments] CHECK CONSTRAINT [CK_Payments_Status]
GO

ALTER TABLE [dbo].[Feedbacks]  WITH CHECK ADD  CONSTRAINT [CK_Feedbacks_Rating] CHECK  (([rating] IS NULL OR [rating]>=(1) AND [rating]<=(5)))
GO

ALTER TABLE [dbo].[Feedbacks] CHECK CONSTRAINT [CK_Feedbacks_Rating]
GO

ALTER TABLE [dbo].[Feedbacks]  WITH CHECK ADD  CONSTRAINT [CK_Feedbacks_Reply_Status] CHECK  (([status]='OPEN' AND ([admin_reply] IS NULL OR len(ltrim(rtrim([admin_reply])))=(0)) OR [status]='RESPONDED' AND [admin_reply] IS NOT NULL AND len(ltrim(rtrim([admin_reply])))>(0) OR [status]='RESOLVED' AND ([rating] IS NULL OR [admin_reply] IS NOT NULL AND len(ltrim(rtrim([admin_reply])))>(0))))
GO

ALTER TABLE [dbo].[Feedbacks] CHECK CONSTRAINT [CK_Feedbacks_Reply_Status]
GO

ALTER TABLE [dbo].[Feedbacks]  WITH CHECK ADD  CONSTRAINT [CK_Feedbacks_Required_Content] CHECK  (([rating] IS NULL OR [rating]>=(4) OR [content] IS NOT NULL AND len(ltrim(rtrim([content])))>(0)))
GO

ALTER TABLE [dbo].[Feedbacks] CHECK CONSTRAINT [CK_Feedbacks_Required_Content]
GO

ALTER TABLE [dbo].[Feedbacks]  WITH CHECK ADD  CONSTRAINT [CK_Feedbacks_Status] CHECK  (([status]='RESOLVED' OR [status]='RESPONDED' OR [status]='OPEN'))
GO

ALTER TABLE [dbo].[Feedbacks] CHECK CONSTRAINT [CK_Feedbacks_Status]
GO

ALTER TABLE [dbo].[Feedback_Cases]  WITH CHECK ADD  CONSTRAINT [CK_Feedback_Cases_ActionApproval] CHECK  ((([staff_action_status]='WAITING_MANAGER' OR [staff_action_status]='AWAITING_STAFF' OR [staff_action_status]='PENDING' OR [staff_action_status]='NOT_PROPOSED') AND [staff_action_approved_by] IS NULL OR ([staff_action_status]='FINALIZED' OR [staff_action_status]='RECOVERY_SETTLED' OR [staff_action_status]='RECOVERY_DUE' OR [staff_action_status]='INTERNAL_ACTION' OR [staff_action_status]='NO_LIABILITY' OR [staff_action_status]='WAITING_MANAGER_APPEAL' OR [staff_action_status]='WAITING_STAFF_APPEAL' OR [staff_action_status]='REJECTED' OR [staff_action_status]='APPROVED') AND [staff_action_approved_by] IS NOT NULL))
GO

ALTER TABLE [dbo].[Feedback_Cases] CHECK CONSTRAINT [CK_Feedback_Cases_ActionApproval]
GO

ALTER TABLE [dbo].[Feedback_Cases]  WITH CHECK ADD  CONSTRAINT [CK_Feedback_Cases_Customer_Estimated_Value] CHECK  (([customer_estimated_value] IS NULL OR [customer_estimated_value]>(0)))
GO

ALTER TABLE [dbo].[Feedback_Cases] CHECK CONSTRAINT [CK_Feedback_Cases_Customer_Estimated_Value]
GO

ALTER TABLE [dbo].[Feedback_Cases]  WITH CHECK ADD  CONSTRAINT [CK_Feedback_Cases_Item_Age_Months] CHECK  (([item_age_months] IS NULL OR [item_age_months]>=(0) AND [item_age_months]<=(1200)))
GO

ALTER TABLE [dbo].[Feedback_Cases] CHECK CONSTRAINT [CK_Feedback_Cases_Item_Age_Months]
GO

ALTER TABLE [dbo].[Feedback_Cases]  WITH CHECK ADD  CONSTRAINT [CK_Feedback_Cases_Remedy] CHECK  (([remedy_type] IS NULL OR ([remedy_type]='OTHER' OR [remedy_type]='COMPENSATION' OR [remedy_type]='REFUND' OR [remedy_type]='PRICE_REDUCTION' OR [remedy_type]='REPLACE' OR [remedy_type]='REPAIR' OR [remedy_type]='RE_CLEAN')))
GO

ALTER TABLE [dbo].[Feedback_Cases] CHECK CONSTRAINT [CK_Feedback_Cases_Remedy]
GO

ALTER TABLE [dbo].[Feedback_Cases]  WITH CHECK ADD  CONSTRAINT [CK_Feedback_Cases_Resolution] CHECK  (([case_status]='RESOLVED' AND [resolved_at] IS NOT NULL OR [case_status]<>'RESOLVED' AND [resolved_at] IS NULL))
GO

ALTER TABLE [dbo].[Feedback_Cases] CHECK CONSTRAINT [CK_Feedback_Cases_Resolution]
GO

ALTER TABLE [dbo].[Feedback_Cases]  WITH CHECK ADD  CONSTRAINT [CK_Feedback_Cases_StaffAction] CHECK  (([staff_action] IS NULL OR ([staff_action]='OTHER' OR [staff_action]='PROPOSE_RECOVERY' OR [staff_action]='PROPOSE_FINAL_WARNING' OR [staff_action]='PROPOSE_WARNING' OR [staff_action]='COACHING' OR [staff_action]='NONE')))
GO

ALTER TABLE [dbo].[Feedback_Cases] CHECK CONSTRAINT [CK_Feedback_Cases_StaffAction]
GO

ALTER TABLE [dbo].[Feedback_Cases]  WITH CHECK ADD  CONSTRAINT [CK_Feedback_Cases_StaffActionStatus] CHECK  (([staff_action_status]='FINALIZED' OR [staff_action_status]='RECOVERY_SETTLED' OR [staff_action_status]='RECOVERY_DUE' OR [staff_action_status]='INTERNAL_ACTION' OR [staff_action_status]='NO_LIABILITY' OR [staff_action_status]='WAITING_MANAGER_APPEAL' OR [staff_action_status]='WAITING_STAFF_APPEAL' OR [staff_action_status]='WAITING_MANAGER' OR [staff_action_status]='AWAITING_STAFF' OR [staff_action_status]='REJECTED' OR [staff_action_status]='APPROVED' OR [staff_action_status]='PENDING' OR [staff_action_status]='NOT_PROPOSED'))
GO

ALTER TABLE [dbo].[Feedback_Cases] CHECK CONSTRAINT [CK_Feedback_Cases_StaffActionStatus]
GO

ALTER TABLE [dbo].[Feedback_Cases]  WITH CHECK ADD  CONSTRAINT [CK_Feedback_Cases_Status] CHECK  (([case_status]='RESOLVED' OR [case_status]='NOT_SUBSTANTIATED' OR [case_status]='VERIFIED' OR [case_status]='WAITING_CUSTOMER_CONFIRMATION' OR [case_status]='WAITING_PAYOUT' OR [case_status]='WAITING_MANAGER' OR [case_status]='WAITING_CUSTOMER' OR [case_status]='SEARCHING_ITEM' OR [case_status]='INVESTIGATING' OR [case_status]='RECEIVED'))
GO

ALTER TABLE [dbo].[Feedback_Cases] CHECK CONSTRAINT [CK_Feedback_Cases_Status]
GO

ALTER TABLE [dbo].[Feedback_Cases]  WITH CHECK ADD  CONSTRAINT [CK_Feedback_Cases_Types] CHECK  ((len([incident_types])>(0) AND NOT [incident_types] like N'%[^A-Z_,]%'))
GO

ALTER TABLE [dbo].[Feedback_Cases] CHECK CONSTRAINT [CK_Feedback_Cases_Types]
GO

ALTER TABLE [dbo].[Booking_Financial_Entries]  WITH CHECK ADD  CONSTRAINT [CK_Booking_Financial_Entries_Amount] CHECK  (([amount]>(0)))
GO

ALTER TABLE [dbo].[Booking_Financial_Entries] CHECK CONSTRAINT [CK_Booking_Financial_Entries_Amount]
GO

ALTER TABLE [dbo].[Booking_Financial_Entries]  WITH CHECK ADD  CONSTRAINT [CK_Booking_Financial_Entries_Approval] CHECK  ((([status]='PENDING_APPROVAL' OR [status]='PROPOSED') AND [approved_by] IS NULL AND [approved_at] IS NULL OR ([status]='SIMULATED' OR [status]='PAID' OR [status]='APPROVED') AND [approved_by] IS NOT NULL AND [approved_at] IS NOT NULL OR ([status]='CANCELLED' OR [status]='REJECTED')))
GO

ALTER TABLE [dbo].[Booking_Financial_Entries] CHECK CONSTRAINT [CK_Booking_Financial_Entries_Approval]
GO

ALTER TABLE [dbo].[Booking_Financial_Entries]  WITH CHECK ADD  CONSTRAINT [CK_Booking_Financial_Entries_Cancelled] CHECK  (([status]='CANCELLED' AND [cancelled_by] IS NOT NULL AND [cancellation_reason] IS NOT NULL OR [status]<>'CANCELLED' AND [cancelled_by] IS NULL AND [cancellation_reason] IS NULL))
GO

ALTER TABLE [dbo].[Booking_Financial_Entries] CHECK CONSTRAINT [CK_Booking_Financial_Entries_Cancelled]
GO

ALTER TABLE [dbo].[Booking_Financial_Entries]  WITH CHECK ADD  CONSTRAINT [CK_Booking_Financial_Entries_Currency] CHECK  (([currency]='VND'))
GO

ALTER TABLE [dbo].[Booking_Financial_Entries] CHECK CONSTRAINT [CK_Booking_Financial_Entries_Currency]
GO

ALTER TABLE [dbo].[Booking_Financial_Entries]  WITH CHECK ADD  CONSTRAINT [CK_Booking_Financial_Entries_Method] CHECK  (([payment_method] IS NULL OR ([payment_method]='OTHER' OR [payment_method]='BANK_TRANSFER' OR [payment_method]='CASH' OR [payment_method]='ONLINE')))
GO

ALTER TABLE [dbo].[Booking_Financial_Entries] CHECK CONSTRAINT [CK_Booking_Financial_Entries_Method]
GO

ALTER TABLE [dbo].[Booking_Financial_Entries]  WITH CHECK ADD  CONSTRAINT [CK_Booking_Financial_Entries_Paid] CHECK  ((([status]='SIMULATED' OR [status]='PAID') AND [paid_at] IS NOT NULL AND [settled_by] IS NOT NULL AND [payment_method] IS NOT NULL AND [receipt_path] IS NOT NULL OR NOT ([status]='SIMULATED' OR [status]='PAID') AND [paid_at] IS NULL AND [settled_by] IS NULL))
GO

ALTER TABLE [dbo].[Booking_Financial_Entries] CHECK CONSTRAINT [CK_Booking_Financial_Entries_Paid]
GO

ALTER TABLE [dbo].[Booking_Financial_Entries]  WITH CHECK ADD  CONSTRAINT [CK_Booking_Financial_Entries_SeparateApprover] CHECK  (([approved_by] IS NULL OR [approved_by]<>[created_by]))
GO

ALTER TABLE [dbo].[Booking_Financial_Entries] CHECK CONSTRAINT [CK_Booking_Financial_Entries_SeparateApprover]
GO

ALTER TABLE [dbo].[Booking_Financial_Entries]  WITH CHECK ADD  CONSTRAINT [CK_Booking_Financial_Entries_Simulation] CHECK  (([status]='SIMULATED' AND [is_simulated]=(1) OR [status]<>'SIMULATED' AND ([status]<>'PAID' OR [is_simulated]=(0))))
GO

ALTER TABLE [dbo].[Booking_Financial_Entries] CHECK CONSTRAINT [CK_Booking_Financial_Entries_Simulation]
GO

ALTER TABLE [dbo].[Booking_Financial_Entries]  WITH CHECK ADD  CONSTRAINT [CK_Booking_Financial_Entries_Status] CHECK  (([status]='SIMULATED' OR [status]='CANCELLED' OR [status]='PAID' OR [status]='REJECTED' OR [status]='APPROVED' OR [status]='PENDING_APPROVAL' OR [status]='PROPOSED'))
GO

ALTER TABLE [dbo].[Booking_Financial_Entries] CHECK CONSTRAINT [CK_Booking_Financial_Entries_Status]
GO

ALTER TABLE [dbo].[Booking_Financial_Entries]  WITH CHECK ADD  CONSTRAINT [CK_Booking_Financial_Entries_Type] CHECK  (([entry_type]='STAFF_RECOVERY' OR [entry_type]='COMPENSATION' OR [entry_type]='REMEDIATION_COST' OR [entry_type]='PRICE_REDUCTION' OR [entry_type]='REFUND'))
GO

ALTER TABLE [dbo].[Booking_Financial_Entries] CHECK CONSTRAINT [CK_Booking_Financial_Entries_Type]
GO

ALTER TABLE [dbo].[Feedback_Case_Verifications]  WITH CHECK ADD  CONSTRAINT [CK_Feedback_Case_Verifications_Status] CHECK  (([case_status]='NOT_SUBSTANTIATED' OR [case_status]='VERIFIED' OR [case_status]='INVESTIGATING'))
GO

ALTER TABLE [dbo].[Feedback_Case_Verifications] CHECK CONSTRAINT [CK_Feedback_Case_Verifications_Status]
GO

ALTER TABLE [dbo].[Feedback_Case_Messages]  WITH CHECK ADD  CONSTRAINT [CK_Feedback_Case_Messages_Role] CHECK  (([sender_role]='ADMIN' OR [sender_role]='CUSTOMER'))
GO

ALTER TABLE [dbo].[Feedback_Case_Messages] CHECK CONSTRAINT [CK_Feedback_Case_Messages_Role]
GO

ALTER TABLE [dbo].[Feedback_Case_Messages]  WITH CHECK ADD  CONSTRAINT [CK_Feedback_Case_Messages_Text] CHECK  (([message_text] IS NULL OR len(ltrim(rtrim([message_text])))>(0) AND ([legacy_imported]=(1) OR len([message_text])<=(4000))))
GO

ALTER TABLE [dbo].[Feedback_Case_Messages] CHECK CONSTRAINT [CK_Feedback_Case_Messages_Text]
GO

ALTER TABLE [dbo].[Staff_Incident_Actions]  WITH CHECK ADD  CONSTRAINT [CK_Staff_Incident_Actions_Acknowledged] CHECK  (([acknowledged_at] IS NULL OR ([status]='RECOVERY_SETTLED' OR [status]='RECOVERY_DUE' OR [status]='INTERNAL_ACTION' OR [status]='NO_LIABILITY' OR [status]='FINALIZED' OR [status]='APPROVED')))
GO

ALTER TABLE [dbo].[Staff_Incident_Actions] CHECK CONSTRAINT [CK_Staff_Incident_Actions_Acknowledged]
GO

ALTER TABLE [dbo].[Staff_Incident_Actions]  WITH CHECK ADD  CONSTRAINT [CK_Staff_Incident_Actions_Decision] CHECK  ((([status]='WAITING_MANAGER' OR [status]='AWAITING_STAFF' OR [status]='PENDING') AND [decided_by] IS NULL AND [decided_at] IS NULL OR NOT ([status]='WAITING_MANAGER' OR [status]='AWAITING_STAFF' OR [status]='PENDING') AND [decided_by] IS NOT NULL AND [decided_at] IS NOT NULL))
GO

ALTER TABLE [dbo].[Staff_Incident_Actions] CHECK CONSTRAINT [CK_Staff_Incident_Actions_Decision]
GO

ALTER TABLE [dbo].[Staff_Incident_Actions]  WITH CHECK ADD  CONSTRAINT [CK_Staff_Incident_Actions_Outcome] CHECK  (([decision_outcome] IS NULL OR ([decision_outcome]='RECOVERY' OR [decision_outcome]='INTERNAL_ACTION' OR [decision_outcome]='NO_LIABILITY')))
GO

ALTER TABLE [dbo].[Staff_Incident_Actions] CHECK CONSTRAINT [CK_Staff_Incident_Actions_Outcome]
GO

ALTER TABLE [dbo].[Staff_Incident_Actions]  WITH CHECK ADD  CONSTRAINT [CK_Staff_Incident_Actions_OutcomeLiability] CHECK  (([decision_outcome] IS NULL OR [decision_outcome]='RECOVERY' AND [liability_amount]>(0) OR ([decision_outcome]='INTERNAL_ACTION' OR [decision_outcome]='NO_LIABILITY') AND coalesce([liability_amount],(0))=(0)))
GO

ALTER TABLE [dbo].[Staff_Incident_Actions] CHECK CONSTRAINT [CK_Staff_Incident_Actions_OutcomeLiability]
GO

ALTER TABLE [dbo].[Staff_Incident_Actions]  WITH CHECK ADD  CONSTRAINT [CK_Staff_Incident_Actions_RecoveryStatus] CHECK  ((NOT ([status]='RECOVERY_SETTLED' OR [status]='RECOVERY_DUE') OR [decision_outcome]='RECOVERY' AND [liability_amount]>(0)))
GO

ALTER TABLE [dbo].[Staff_Incident_Actions] CHECK CONSTRAINT [CK_Staff_Incident_Actions_RecoveryStatus]
GO

ALTER TABLE [dbo].[Staff_Incident_Actions]  WITH CHECK ADD  CONSTRAINT [CK_Staff_Incident_Actions_Separation] CHECK  (([decided_by] IS NULL OR [decided_by]<>[proposed_by]))
GO

ALTER TABLE [dbo].[Staff_Incident_Actions] CHECK CONSTRAINT [CK_Staff_Incident_Actions_Separation]
GO

ALTER TABLE [dbo].[Staff_Incident_Actions]  WITH CHECK ADD  CONSTRAINT [CK_Staff_Incident_Actions_Status] CHECK  (([status]='FINALIZED' OR [status]='RECOVERY_SETTLED' OR [status]='RECOVERY_DUE' OR [status]='INTERNAL_ACTION' OR [status]='NO_LIABILITY' OR [status]='WAITING_MANAGER_APPEAL' OR [status]='WAITING_STAFF_APPEAL' OR [status]='WAITING_MANAGER' OR [status]='AWAITING_STAFF' OR [status]='REJECTED' OR [status]='APPROVED' OR [status]='PENDING'))
GO

ALTER TABLE [dbo].[Staff_Incident_Actions] CHECK CONSTRAINT [CK_Staff_Incident_Actions_Status]
GO

ALTER TABLE [dbo].[Staff_Incident_Actions]  WITH CHECK ADD  CONSTRAINT [CK_Staff_Incident_Actions_Type] CHECK  (([action_type]='OTHER' OR [action_type]='PROPOSE_RECOVERY' OR [action_type]='PROPOSE_FINAL_WARNING' OR [action_type]='PROPOSE_WARNING' OR [action_type]='COACHING' OR [action_type]='NONE'))
GO

ALTER TABLE [dbo].[Staff_Incident_Actions] CHECK CONSTRAINT [CK_Staff_Incident_Actions_Type]
GO

ALTER TABLE [dbo].[Staff_Recovery_Installments]  WITH CHECK ADD  CONSTRAINT [CK_Staff_Recovery_Installments_Amount] CHECK  (([amount]>(0)))
GO

ALTER TABLE [dbo].[Staff_Recovery_Installments] CHECK CONSTRAINT [CK_Staff_Recovery_Installments_Amount]
GO

ALTER TABLE [dbo].[Staff_Recovery_Installments]  WITH CHECK ADD  CONSTRAINT [CK_Staff_Recovery_Installments_Method] CHECK  (([payment_method]='OTHER' OR [payment_method]='BANK_TRANSFER' OR [payment_method]='CASH'))
GO

ALTER TABLE [dbo].[Staff_Recovery_Installments] CHECK CONSTRAINT [CK_Staff_Recovery_Installments_Method]
GO

ALTER TABLE [dbo].[Staff_Incident_Action_Evidence]  WITH CHECK ADD  CONSTRAINT [CK_Staff_Action_Evidence_Stage] CHECK  (([evidence_stage]='APPEAL' OR [evidence_stage]='RESPONSE'))
GO

ALTER TABLE [dbo].[Staff_Incident_Action_Evidence] CHECK CONSTRAINT [CK_Staff_Action_Evidence_Stage]
GO

ALTER TABLE [dbo].[Financial_Payout_Responses]  WITH CHECK ADD  CONSTRAINT [CK_Financial_Payout_Responses_Choice] CHECK  (([response_status]='AGREED_AMOUNT' AND [payout_method] IS NULL AND [encrypted_account_details] IS NULL OR [response_status]='NEEDS_DISCUSSION' AND [payout_method] IS NULL AND [encrypted_account_details] IS NULL OR [response_status]='ACCEPTED' AND [payout_method]='CASH' AND [encrypted_account_details] IS NULL OR [response_status]='ACCEPTED' AND [payout_method]='BANK_TRANSFER' AND [encrypted_account_details] IS NOT NULL OR [response_status]='SETTLED' AND ([payout_method]='BANK_TRANSFER' OR [payout_method]='CASH') AND [encrypted_account_details] IS NULL AND [payout_method_selected_at] IS NOT NULL))
GO

ALTER TABLE [dbo].[Financial_Payout_Responses] CHECK CONSTRAINT [CK_Financial_Payout_Responses_Choice]
GO

ALTER TABLE [dbo].[Financial_Payout_Responses]  WITH CHECK ADD  CONSTRAINT [CK_Financial_Payout_Responses_Method] CHECK  (([payout_method] IS NULL OR ([payout_method]='CASH' OR [payout_method]='BANK_TRANSFER')))
GO

ALTER TABLE [dbo].[Financial_Payout_Responses] CHECK CONSTRAINT [CK_Financial_Payout_Responses_Method]
GO

ALTER TABLE [dbo].[Financial_Payout_Responses]  WITH CHECK ADD  CONSTRAINT [CK_Financial_Payout_Responses_Status] CHECK  (([response_status]='SETTLED' OR [response_status]='NEEDS_DISCUSSION' OR [response_status]='ACCEPTED' OR [response_status]='AGREED_AMOUNT'))
GO

ALTER TABLE [dbo].[Financial_Payout_Responses] CHECK CONSTRAINT [CK_Financial_Payout_Responses_Status]
GO

ALTER TABLE [dbo].[Feedback_Case_Evidence]  WITH CHECK ADD  CONSTRAINT [CK_Feedback_Case_Evidence_Media_Source] CHECK  (([image_path] IS NOT NULL AND [external_url] IS NULL OR [image_path] IS NULL AND [external_url] IS NOT NULL))
GO

ALTER TABLE [dbo].[Feedback_Case_Evidence] CHECK CONSTRAINT [CK_Feedback_Case_Evidence_Media_Source]
GO

ALTER TABLE [dbo].[Feedback_Case_Evidence]  WITH CHECK ADD  CONSTRAINT [CK_Feedback_Case_Evidence_Media_Type] CHECK  (([media_type]='DRIVE_LINK' OR [media_type]='VIDEO' OR [media_type]='PDF' OR [media_type]='IMAGE'))
GO

ALTER TABLE [dbo].[Feedback_Case_Evidence] CHECK CONSTRAINT [CK_Feedback_Case_Evidence_Media_Type]
GO
