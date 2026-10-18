/*
 * Local demo bootstrap. Run this whole file in SSMS using a sysadmin account.
 * Public example SQL credentials; never use them for a deployed environment.
 * This creates only the database/login. Run Housekeeping Demo afterwards:
 * Flyway creates the schema and DemoAccountSeeder creates all three web users.
 * Repeatable: no DROP, password reset, schema baseline, or data deletion.
 */
USE [master];
SET NOCOUNT ON;
SET XACT_ABORT ON;

IF ISNULL(IS_SRVROLEMEMBER(N'sysadmin'), 0) <> 1
    THROW 51000, 'Run setup-demo.sql with a SQL Server sysadmin account.', 1;

IF CONVERT(int, SERVERPROPERTY('IsIntegratedSecurityOnly')) = 1
    THROW 51001, 'Enable SQL Server and Windows Authentication mode, restart SQL Server, then run this script again.', 1;

IF EXISTS (SELECT 1 FROM sys.server_principals WHERE name = N'housekeeping_demo_app')
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM sys.sql_logins
        WHERE name = N'housekeeping_demo_app'
          AND is_disabled = 0
          AND PWDCOMPARE(N'HousekeepingDemo@2026', password_hash) = 1
          AND is_expiration_checked = 0
    )
        THROW 51002, 'Existing housekeeping_demo_app login differs from the demo defaults. Nothing was reset. Use your own DB_* settings or resolve this login conflict.', 1;
END;

IF DB_ID(N'housekeeping_demo') IS NULL
    EXEC(N'CREATE DATABASE [housekeeping_demo]');

IF NOT EXISTS (SELECT 1 FROM sys.server_principals WHERE name = N'housekeeping_demo_app')
    CREATE LOGIN [housekeeping_demo_app]
        WITH PASSWORD = N'HousekeepingDemo@2026',
             DEFAULT_DATABASE = [housekeeping_demo],
             CHECK_POLICY = ON, CHECK_EXPIRATION = OFF;

EXEC(N'
USE [housekeeping_demo];
SET XACT_ABORT ON;
BEGIN TRY
    BEGIN TRANSACTION;
    IF DATABASE_PRINCIPAL_ID(N''housekeeping_demo_app'') IS NULL
        CREATE USER [housekeeping_demo_app] FOR LOGIN [housekeeping_demo_app];
    IF NOT EXISTS (
        SELECT 1 FROM sys.database_principals
        WHERE name = N''housekeeping_demo_app''
          AND sid = SUSER_SID(N''housekeeping_demo_app'') AND type = ''S''
    )
        THROW 51003, ''Existing database user has a different login mapping. No user was overwritten.'', 1;
    IF IS_ROLEMEMBER(N''db_datareader'', N''housekeeping_demo_app'') <> 1
        ALTER ROLE [db_datareader] ADD MEMBER [housekeeping_demo_app];
    IF IS_ROLEMEMBER(N''db_datawriter'', N''housekeeping_demo_app'') <> 1
        ALTER ROLE [db_datawriter] ADD MEMBER [housekeeping_demo_app];
    IF IS_ROLEMEMBER(N''db_ddladmin'', N''housekeeping_demo_app'') <> 1
        ALTER ROLE [db_ddladmin] ADD MEMBER [housekeeping_demo_app];
    GRANT CONNECT TO [housekeeping_demo_app];
    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
');

PRINT N'Demo database/login ready. Enable TCP/IP on port 1433 for this instance, then Run Housekeeping Demo (Java 21).';
PRINT N'Web: http://localhost:8080/login';
PRINT N'Admin: admin@demo.gonnha.local | Customer: customer@demo.gonnha.local | Staff: staff@demo.gonnha.local';
PRINT N'Web password for all three roles: Demo@123456';
