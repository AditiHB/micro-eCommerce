-- Service-to-service identity for Notification Service's CustomerClient,
-- which calls GET /api/customers/{id} directly (not through the gateway) to
-- resolve a customer's email for notifications. That endpoint requires an
-- authenticated USER/ADMIN/MANAGER (see common.security.SecurityConfig), so
-- the caller mints a JWT for this username (JwtTokenProvider.
-- generateTokenFromUsername) - which only works if this service's own local
-- users table (database-per-service) has a matching row to authorize
-- against. USER is enough; this account is never used to log in with a
-- password, only as a JWT subject.
INSERT INTO users (username, email, password, role, enabled) VALUES
    ('notification-service-account', 'notification-service-account@internal.local', '$2b$10$p2A7sCROhW8KaHCXzWfg1evqs6I9O14ehK02oz93nXsNsiHBtyZF.', 'USER', TRUE);
