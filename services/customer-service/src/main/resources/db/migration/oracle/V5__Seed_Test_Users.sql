-- Seed one ADMIN test user so the JWT login flow (POST /api/auth/login) has
-- something to authenticate against out of the box - the users table was
-- otherwise created empty with no way to self-register (AuthController only
-- exposes /login and /me, not a register endpoint). ADMIN satisfies every
-- role check in SecurityConfig, so this single account is enough to exercise
-- the full customer -> order -> payment -> inventory API surface, including
-- the e2e-tests Karate module.
--
-- Password is "KarateTest123!" (bcrypt hash below) - local/dev/test use only.
INSERT INTO users (username, email, password, role, enabled) VALUES
    ('karate_admin', 'karate.admin@example.com', '$2b$10$p2A7sCROhW8KaHCXzWfg1evqs6I9O14ehK02oz93nXsNsiHBtyZF.', 'ADMIN', 1);
