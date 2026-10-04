-- Same seeded ADMIN test user as customer-service (see its
-- V6__Seed_Test_Users.sql for why) - each service keeps its own local
-- replica of the users table, so the login-granted JWT can only be
-- validated here if this table also has the user. Keep username/password
-- hash identical across every service so one login works everywhere.
INSERT INTO users (username, email, password, role, enabled) VALUES
    ('karate_admin', 'karate.admin@example.com', '$2b$10$p2A7sCROhW8KaHCXzWfg1evqs6I9O14ehK02oz93nXsNsiHBtyZF.', 'ADMIN', TRUE);
