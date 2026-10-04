-- An application-level "does a payment already exist for this order" check
-- (PaymentEventListener.handleInventoryReserved) is still a check-then-act
-- race: the REST API (POST /api/payments) and the inventory-reserved Kafka
-- event are two independent triggers that can both pass that check before
-- either one writes, creating two payments for the same order (confirmed
-- happening via e2e-tests/customer-journey.feature - see payments table
-- rows for the same order_id with different amounts). A unique constraint
-- is the actual, atomic source of truth for "one payment per order".
--
-- Clean up any duplicates already created by that race before the
-- constraint can be added (keep the earliest payment per order).
DELETE FROM payments
WHERE id NOT IN (
    SELECT MIN(id) FROM payments GROUP BY order_id
);

ALTER TABLE payments ADD CONSTRAINT uq_payments_order_id UNIQUE (order_id);
