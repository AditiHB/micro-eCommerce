-- Identity moved to the external identity provider (Keycloak): this service no longer stores
-- credentials or user accounts, so the local `users` table - and the seeded test/service accounts
-- earlier migrations inserted into it - are removed. Earlier migrations are left untouched because
-- Flyway checksums applied versions; this one removes their effect.
DROP TABLE IF EXISTS users;
