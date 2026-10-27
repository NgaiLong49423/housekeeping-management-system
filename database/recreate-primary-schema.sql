/*
 * DESTRUCTIVE, ONE-TIME reset for the local primary application database.
 * Removes every application table and its data, plus the new primary Flyway
 * history table. Keeps the legacy flyway_schema_history unchanged.
 * Run only after the database owner confirms the old data is no longer needed.
 */
SET NOCOUNT ON;
SET XACT_ABORT ON;

IF DB_NAME() <> N'Housekeeping_Database'
    THROW 51200, 'Refusing to reset: connect directly to Housekeeping_Database first.', 1;

BEGIN TRY
    BEGIN TRANSACTION;

    DECLARE @sql NVARCHAR(MAX) = N'';

    SELECT @sql += N'ALTER TABLE ' + QUOTENAME(SCHEMA_NAME(parent_table.schema_id)) + N'.'
        + QUOTENAME(parent_table.name) + N' DROP CONSTRAINT ' + QUOTENAME(foreign_key.name) + N';'
    FROM sys.foreign_keys AS foreign_key
    INNER JOIN sys.tables AS parent_table ON parent_table.object_id = foreign_key.parent_object_id;

    IF @sql <> N''
        EXEC sys.sp_executesql @sql;

    SET @sql = N'';
    SELECT @sql += N'DROP TABLE ' + QUOTENAME(SCHEMA_NAME(user_table.schema_id)) + N'.'
        + QUOTENAME(user_table.name) + N';'
    FROM sys.tables AS user_table
    WHERE user_table.is_ms_shipped = 0
      AND UPPER(user_table.name) <> N'FLYWAY_SCHEMA_HISTORY';

    IF @sql <> N''
        EXEC sys.sp_executesql @sql;

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0
        ROLLBACK TRANSACTION;
    THROW;
END CATCH;
