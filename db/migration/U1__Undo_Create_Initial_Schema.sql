-- ============================================================================
-- Micro-eCommerce Database Schema Undo
-- Undo Version 1: Remove Initial Schema
-- Description: Drops all tables (for Flyway undo functionality)
-- Note: This is an optional undo script for development/testing
-- ============================================================================

-- Drop tables in reverse order of creation to handle foreign keys
DROP TABLE IF EXISTS payments CASCADE;
DROP TABLE IF EXISTS orders CASCADE;
DROP TABLE IF EXISTS inventory CASCADE;
DROP TABLE IF EXISTS customers CASCADE;

-- ============================================================================
-- Schema cleanup complete
-- ============================================================================
