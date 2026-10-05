-- Identity moved to the external identity provider (Keycloak): this service no longer stores
-- credentials or user accounts, so the local USERS table - and the seeded test/service accounts
-- earlier migrations inserted into it - are removed. Earlier migrations are left untouched because
-- Flyway checksums applied versions; this one removes their effect.
BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE users CASCADE CONSTRAINTS';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -942 THEN
            RAISE;
        END IF;
END;
/
