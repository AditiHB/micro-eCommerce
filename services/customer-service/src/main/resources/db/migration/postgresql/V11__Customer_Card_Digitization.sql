-- Encrypted last-4-digits "card on file", one per customer. card_ciphertext stores the JWE compact
-- serialization (header.encryptedKey.iv.ciphertext.tag) as URL-safe base64 text, so it stays TEXT like
-- every other column in this service rather than introducing a BYTEA. card_last_four is deliberately kept
-- in plain text - PCI-DSS Requirement 3.3 explicitly allows a truncated PAN to be stored unencrypted.
ALTER TABLE customers ADD COLUMN card_ciphertext TEXT;
ALTER TABLE customers ADD COLUMN card_last_four VARCHAR(4);
