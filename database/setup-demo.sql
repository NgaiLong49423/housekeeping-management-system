/*
 * Create only the isolated local demo database. This script never creates a login,
 * embeds a password, resets a credential, drops a database, or deletes user data.
 * Configure a SQL login and local .env separately using credentials owned by you.
 */
USE [master];
SET NOCOUNT ON;
SET XACT_ABORT ON;

IF DB_ID(N'home_cleaning_demo') IS NULL
BEGIN
    EXEC(N'CREATE DATABASE [home_cleaning_demo]');
    PRINT N'Created isolated database home_cleaning_demo.';
END
ELSE
BEGIN
    PRINT N'Database home_cleaning_demo already exists; no database contents were changed.';
END;

PRINT N'Next: create/configure a SQL Server login with access to this disposable database, then set DB_URL, DB_USERNAME, and DB_PASSWORD in app/housekeeping-management-system/.env.';
