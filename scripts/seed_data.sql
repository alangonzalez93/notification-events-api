-- Seed data for demo — run after `docker compose up`
-- Creates 3 clients with subscriptions covering all event types
-- and 40 notification events in varied statuses across multiple dates

INSERT INTO clients (unique_code, deleted, created_date, last_modified_date, name, email)
VALUES
    ('CLIENT001', false, NOW(), NOW(), 'Acme Corp',      'acme@demo.com'),
    ('CLIENT002', false, NOW(), NOW(), 'Beta Finance',   'beta@demo.com'),
    ('CLIENT003', false, NOW(), NOW(), 'Gamma Payments', 'gamma@demo.com');

INSERT INTO subscriptions (unique_code, deleted, created_date, last_modified_date, client_id, webhook_url, auth_header_name, auth_header_value, active)
VALUES
    ('SUB001', false, NOW(), NOW(), (SELECT id FROM clients WHERE unique_code = 'CLIENT001'), 'https://webhook.site/placeholder', 'X-Webhook-Secret', 'secret-client001', true),
    ('SUB002', false, NOW(), NOW(), (SELECT id FROM clients WHERE unique_code = 'CLIENT002'), 'https://webhook.site/placeholder', 'X-Webhook-Secret', 'secret-client002', true),
    ('SUB003', false, NOW(), NOW(), (SELECT id FROM clients WHERE unique_code = 'CLIENT003'), 'https://webhook.site/placeholder', 'X-Webhook-Secret', 'secret-client003', true);

-- Subscriptions cover all event types
INSERT INTO subscription_event_types (subscription_id, event_type)
SELECT id, event_type FROM subscriptions
CROSS JOIN (
    SELECT 'CREDIT_CARD_PAYMENT'    AS event_type UNION ALL
    SELECT 'DEBIT_CARD_WITHDRAWAL'  UNION ALL
    SELECT 'CREDIT_TRANSFER'        UNION ALL
    SELECT 'DEBIT_AUTOMATIC_PAYMENT' UNION ALL
    SELECT 'CREDIT_REFUND'          UNION ALL
    SELECT 'DEBIT_TRANSFER'         UNION ALL
    SELECT 'CREDIT_DEPOSIT'         UNION ALL
    SELECT 'DEBIT_PURCHASE'         UNION ALL
    SELECT 'CREDIT_CASHBACK'        UNION ALL
    SELECT 'DEBIT_SUBSCRIPTION'
) event_types
WHERE unique_code IN ('SUB001', 'SUB002', 'SUB003');

-- ─────────────────────────────────────────────────────────────────────────────
-- Notification events — 40 records across 3 clients, varied statuses and dates
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO notification_events (unique_code, deleted, created_date, last_modified_date, event_id, event_type, payload, client_id, subscription_id, delivery_status, delivered_at, retry_count, next_retry_at, last_error, version)
VALUES

-- ── CLIENT001 (Acme Corp) ────────────────────────────────────────────────────
('EVT001', false, '2024-03-15 09:30:22', '2024-03-15 09:30:25', 'EVT001', 'CREDIT_CARD_PAYMENT',    'Credit card payment received for $150.00',
 (SELECT id FROM clients WHERE unique_code='CLIENT001'), (SELECT id FROM subscriptions WHERE unique_code='SUB001'),
 'DELIVERED', '2024-03-15 09:30:25', 0, NULL, NULL, 0),

('EVT002', false, '2024-03-15 10:15:45', '2024-03-15 10:15:48', 'EVT002', 'DEBIT_CARD_WITHDRAWAL',  'ATM withdrawal of $200.00',
 (SELECT id FROM clients WHERE unique_code='CLIENT001'), (SELECT id FROM subscriptions WHERE unique_code='SUB001'),
 'DELIVERED', '2024-03-15 10:15:48', 0, NULL, NULL, 0),

('EVT007', false, '2024-03-15 15:20:40', '2024-03-15 15:20:43', 'EVT007', 'CREDIT_DEPOSIT',         'Direct deposit received from Employer XYZ for $2,500.00',
 (SELECT id FROM clients WHERE unique_code='CLIENT001'), (SELECT id FROM subscriptions WHERE unique_code='SUB001'),
 'DELIVERED', '2024-03-15 15:20:43', 0, NULL, NULL, 0),

('EVT010', false, '2024-03-15 18:05:12', '2024-03-15 18:05:12', 'EVT010', 'DEBIT_SUBSCRIPTION',     'Monthly streaming service payment of $14.99',
 (SELECT id FROM clients WHERE unique_code='CLIENT001'), (SELECT id FROM subscriptions WHERE unique_code='SUB001'),
 'PENDING', NULL, 0, NULL, NULL, 0),

('EVT011', false, '2024-03-16 08:10:00', '2024-03-16 08:10:33', 'EVT011', 'CREDIT_TRANSFER',        'Wire transfer received from Account #1122 for $3,000.00',
 (SELECT id FROM clients WHERE unique_code='CLIENT001'), (SELECT id FROM subscriptions WHERE unique_code='SUB001'),
 'DELIVERED', '2024-03-16 08:10:33', 0, NULL, NULL, 0),

('EVT012', false, '2024-03-16 09:45:00', '2024-03-16 09:45:05', 'EVT012', 'DEBIT_PURCHASE',         'Online purchase at Amazon for $89.99',
 (SELECT id FROM clients WHERE unique_code='CLIENT001'), (SELECT id FROM subscriptions WHERE unique_code='SUB001'),
 'DELIVERED', '2024-03-16 09:45:05', 0, NULL, NULL, 0),

('EVT013', false, '2024-03-16 14:30:00', '2024-03-16 14:30:18', 'EVT013', 'CREDIT_CASHBACK',        'Quarterly cashback reward of $45.00',
 (SELECT id FROM clients WHERE unique_code='CLIENT001'), (SELECT id FROM subscriptions WHERE unique_code='SUB001'),
 'DELIVERED', '2024-03-16 14:30:18', 0, NULL, NULL, 0),

('EVT014', false, '2024-03-17 07:00:00', '2024-03-17 07:00:07', 'EVT014', 'DEBIT_AUTOMATIC_PAYMENT','Mortgage payment of $1,200.00',
 (SELECT id FROM clients WHERE unique_code='CLIENT001'), (SELECT id FROM subscriptions WHERE unique_code='SUB001'),
 'DELIVERED', '2024-03-17 07:00:07', 0, NULL, NULL, 0),

('EVT015', false, '2024-03-17 11:20:00', '2024-03-17 11:20:42', 'EVT015', 'CREDIT_REFUND',          'Refund from airline cancellation for $320.00',
 (SELECT id FROM clients WHERE unique_code='CLIENT001'), (SELECT id FROM subscriptions WHERE unique_code='SUB001'),
 'FAILED', NULL, 4, NULL, 'HTTP 503 Service Unavailable', 0),

('EVT016', false, '2024-03-17 16:55:00', '2024-03-17 16:55:00', 'EVT016', 'DEBIT_TRANSFER',         'Transfer to savings account for $500.00',
 (SELECT id FROM clients WHERE unique_code='CLIENT001'), (SELECT id FROM subscriptions WHERE unique_code='SUB001'),
 'PENDING', NULL, 1, DATE_ADD(NOW(), INTERVAL 10 SECOND), 'HTTP 502 Bad Gateway', 0),

('EVT017', false, '2024-03-18 09:00:00', '2024-03-18 09:00:03', 'EVT017', 'CREDIT_CARD_PAYMENT',    'Credit card payment received for $430.00',
 (SELECT id FROM clients WHERE unique_code='CLIENT001'), (SELECT id FROM subscriptions WHERE unique_code='SUB001'),
 'DELIVERED', '2024-03-18 09:00:03', 0, NULL, NULL, 0),

('EVT018', false, '2024-03-18 13:15:00', '2024-03-18 13:15:00', 'EVT018', 'DEBIT_CARD_WITHDRAWAL',  'ATM withdrawal of $100.00',
 (SELECT id FROM clients WHERE unique_code='CLIENT001'), (SELECT id FROM subscriptions WHERE unique_code='SUB001'),
 'PENDING', NULL, 0, NULL, NULL, 0),

-- ── CLIENT002 (Beta Finance) ─────────────────────────────────────────────────
('EVT003', false, '2024-03-15 11:20:18', '2024-03-15 11:20:18', 'EVT003', 'CREDIT_TRANSFER',        'Bank transfer received from Account #4567 for $1,500.00',
 (SELECT id FROM clients WHERE unique_code='CLIENT002'), (SELECT id FROM subscriptions WHERE unique_code='SUB002'),
 'FAILED', NULL, 4, NULL, 'HTTP 503 Service Unavailable', 0),

('EVT004', false, '2024-03-15 12:05:33', '2024-03-15 12:05:36', 'EVT004', 'DEBIT_AUTOMATIC_PAYMENT','Monthly utility bill payment of $85.50',
 (SELECT id FROM clients WHERE unique_code='CLIENT002'), (SELECT id FROM subscriptions WHERE unique_code='SUB002'),
 'DELIVERED', '2024-03-15 12:05:36', 0, NULL, NULL, 0),

('EVT008', false, '2024-03-15 16:10:15', '2024-03-15 16:10:18', 'EVT008', 'DEBIT_PURCHASE',         'Point of sale purchase at Store ABC for $75.25',
 (SELECT id FROM clients WHERE unique_code='CLIENT002'), (SELECT id FROM subscriptions WHERE unique_code='SUB002'),
 'DELIVERED', '2024-03-15 16:10:18', 0, NULL, NULL, 0),

('EVT019', false, '2024-03-16 08:30:00', '2024-03-16 08:30:04', 'EVT019', 'CREDIT_DEPOSIT',         'Payroll deposit for $4,200.00',
 (SELECT id FROM clients WHERE unique_code='CLIENT002'), (SELECT id FROM subscriptions WHERE unique_code='SUB002'),
 'DELIVERED', '2024-03-16 08:30:04', 0, NULL, NULL, 0),

('EVT020', false, '2024-03-16 10:00:00', '2024-03-16 10:00:09', 'EVT020', 'CREDIT_CARD_PAYMENT',    'Credit card payment received for $675.00',
 (SELECT id FROM clients WHERE unique_code='CLIENT002'), (SELECT id FROM subscriptions WHERE unique_code='SUB002'),
 'DELIVERED', '2024-03-16 10:00:09', 0, NULL, NULL, 0),

('EVT021', false, '2024-03-16 15:45:00', '2024-03-16 15:45:00', 'EVT021', 'DEBIT_TRANSFER',         'International transfer to Account #9988 for $800.00',
 (SELECT id FROM clients WHERE unique_code='CLIENT002'), (SELECT id FROM subscriptions WHERE unique_code='SUB002'),
 'FAILED', NULL, 4, NULL, 'Connection timeout after 10000ms', 0),

('EVT022', false, '2024-03-17 09:20:00', '2024-03-17 09:20:06', 'EVT022', 'CREDIT_CASHBACK',        'Annual cashback reward of $120.00',
 (SELECT id FROM clients WHERE unique_code='CLIENT002'), (SELECT id FROM subscriptions WHERE unique_code='SUB002'),
 'DELIVERED', '2024-03-17 09:20:06', 0, NULL, NULL, 0),

('EVT023', false, '2024-03-17 14:00:00', '2024-03-17 14:00:00', 'EVT023', 'CREDIT_REFUND',          'Merchant refund for returned goods $210.00',
 (SELECT id FROM clients WHERE unique_code='CLIENT002'), (SELECT id FROM subscriptions WHERE unique_code='SUB002'),
 'PENDING', NULL, 2, DATE_ADD(NOW(), INTERVAL 20 SECOND), 'HTTP 500 Internal Server Error', 0),

('EVT024', false, '2024-03-17 17:30:00', '2024-03-17 17:30:02', 'EVT024', 'DEBIT_CARD_WITHDRAWAL',  'ATM withdrawal of $300.00',
 (SELECT id FROM clients WHERE unique_code='CLIENT002'), (SELECT id FROM subscriptions WHERE unique_code='SUB002'),
 'DELIVERED', '2024-03-17 17:30:02', 0, NULL, NULL, 0),

('EVT025', false, '2024-03-18 08:00:00', '2024-03-18 08:00:05', 'EVT025', 'DEBIT_AUTOMATIC_PAYMENT','Car insurance payment of $180.00',
 (SELECT id FROM clients WHERE unique_code='CLIENT002'), (SELECT id FROM subscriptions WHERE unique_code='SUB002'),
 'DELIVERED', '2024-03-18 08:00:05', 0, NULL, NULL, 0),

('EVT026', false, '2024-03-18 11:45:00', '2024-03-18 11:45:00', 'EVT026', 'DEBIT_SUBSCRIPTION',     'Cloud storage subscription $9.99',
 (SELECT id FROM clients WHERE unique_code='CLIENT002'), (SELECT id FROM subscriptions WHERE unique_code='SUB002'),
 'PENDING', NULL, 0, NULL, NULL, 0),

('EVT027', false, '2024-03-18 14:20:00', '2024-03-18 14:20:08', 'EVT027', 'CREDIT_TRANSFER',        'Freelance payment received for $950.00',
 (SELECT id FROM clients WHERE unique_code='CLIENT002'), (SELECT id FROM subscriptions WHERE unique_code='SUB002'),
 'DELIVERED', '2024-03-18 14:20:08', 0, NULL, NULL, 0),

-- ── CLIENT003 (Gamma Payments) ───────────────────────────────────────────────
('EVT005', false, '2024-03-15 13:45:10', '2024-03-15 13:45:10', 'EVT005', 'CREDIT_REFUND',          'Refund processed for order #789 for $45.99',
 (SELECT id FROM clients WHERE unique_code='CLIENT003'), (SELECT id FROM subscriptions WHERE unique_code='SUB003'),
 'FAILED', NULL, 4, NULL, 'Connection timeout after 10000ms', 0),

('EVT006', false, '2024-03-15 14:30:55', '2024-03-15 14:30:58', 'EVT006', 'DEBIT_TRANSFER',         'Money transfer sent to Account #8901 for $500.00',
 (SELECT id FROM clients WHERE unique_code='CLIENT003'), (SELECT id FROM subscriptions WHERE unique_code='SUB003'),
 'DELIVERED', '2024-03-15 14:30:58', 0, NULL, NULL, 0),

('EVT009', false, '2024-03-15 17:25:30', '2024-03-15 17:25:30', 'EVT009', 'CREDIT_CASHBACK',        'Cashback reward credited for $25.00',
 (SELECT id FROM clients WHERE unique_code='CLIENT003'), (SELECT id FROM subscriptions WHERE unique_code='SUB003'),
 'FAILED', NULL, 4, NULL, 'HTTP 500 Internal Server Error', 0),

('EVT028', false, '2024-03-16 07:30:00', '2024-03-16 07:30:04', 'EVT028', 'CREDIT_DEPOSIT',         'Business revenue deposit of $8,750.00',
 (SELECT id FROM clients WHERE unique_code='CLIENT003'), (SELECT id FROM subscriptions WHERE unique_code='SUB003'),
 'DELIVERED', '2024-03-16 07:30:04', 0, NULL, NULL, 0),

('EVT029', false, '2024-03-16 10:15:00', '2024-03-16 10:15:06', 'EVT029', 'DEBIT_PURCHASE',         'Supplier payment at Wholesale Co for $1,340.00',
 (SELECT id FROM clients WHERE unique_code='CLIENT003'), (SELECT id FROM subscriptions WHERE unique_code='SUB003'),
 'DELIVERED', '2024-03-16 10:15:06', 0, NULL, NULL, 0),

('EVT030', false, '2024-03-16 13:00:00', '2024-03-16 13:00:00', 'EVT030', 'CREDIT_CARD_PAYMENT',    'Credit card payment received for $2,200.00',
 (SELECT id FROM clients WHERE unique_code='CLIENT003'), (SELECT id FROM subscriptions WHERE unique_code='SUB003'),
 'FAILED', NULL, 4, NULL, 'HTTP 503 Service Unavailable', 0),

('EVT031', false, '2024-03-16 16:40:00', '2024-03-16 16:40:03', 'EVT031', 'DEBIT_AUTOMATIC_PAYMENT','Office rent payment of $3,500.00',
 (SELECT id FROM clients WHERE unique_code='CLIENT003'), (SELECT id FROM subscriptions WHERE unique_code='SUB003'),
 'DELIVERED', '2024-03-16 16:40:03', 0, NULL, NULL, 0),

('EVT032', false, '2024-03-17 08:00:00', '2024-03-17 08:00:05', 'EVT032', 'CREDIT_TRANSFER',        'Client payment for Invoice #4421 for $5,600.00',
 (SELECT id FROM clients WHERE unique_code='CLIENT003'), (SELECT id FROM subscriptions WHERE unique_code='SUB003'),
 'DELIVERED', '2024-03-17 08:00:05', 0, NULL, NULL, 0),

('EVT033', false, '2024-03-17 10:30:00', '2024-03-17 10:30:00', 'EVT033', 'DEBIT_CARD_WITHDRAWAL',  'Cash withdrawal of $400.00',
 (SELECT id FROM clients WHERE unique_code='CLIENT003'), (SELECT id FROM subscriptions WHERE unique_code='SUB003'),
 'PENDING', NULL, 0, NULL, NULL, 0),

('EVT034', false, '2024-03-17 13:50:00', '2024-03-17 13:50:09', 'EVT034', 'CREDIT_REFUND',          'Tax refund credited for $890.00',
 (SELECT id FROM clients WHERE unique_code='CLIENT003'), (SELECT id FROM subscriptions WHERE unique_code='SUB003'),
 'DELIVERED', '2024-03-17 13:50:09', 0, NULL, NULL, 0),

('EVT035', false, '2024-03-17 17:10:00', '2024-03-17 17:10:00', 'EVT035', 'DEBIT_SUBSCRIPTION',     'SaaS platform subscription $299.00',
 (SELECT id FROM clients WHERE unique_code='CLIENT003'), (SELECT id FROM subscriptions WHERE unique_code='SUB003'),
 'PENDING', NULL, 1, DATE_ADD(NOW(), INTERVAL 5 SECOND), 'HTTP 502 Bad Gateway', 0),

('EVT036', false, '2024-03-18 07:45:00', '2024-03-18 07:45:07', 'EVT036', 'DEBIT_PURCHASE',         'Equipment purchase for $2,100.00',
 (SELECT id FROM clients WHERE unique_code='CLIENT003'), (SELECT id FROM subscriptions WHERE unique_code='SUB003'),
 'DELIVERED', '2024-03-18 07:45:07', 0, NULL, NULL, 0),

('EVT037', false, '2024-03-18 09:30:00', '2024-03-18 09:30:04', 'EVT037', 'CREDIT_CASHBACK',        'Business cashback reward of $78.00',
 (SELECT id FROM clients WHERE unique_code='CLIENT003'), (SELECT id FROM subscriptions WHERE unique_code='SUB003'),
 'DELIVERED', '2024-03-18 09:30:04', 0, NULL, NULL, 0),

('EVT038', false, '2024-03-18 11:00:00', '2024-03-18 11:00:00', 'EVT038', 'DEBIT_TRANSFER',         'Payroll transfer to employee accounts for $12,400.00',
 (SELECT id FROM clients WHERE unique_code='CLIENT003'), (SELECT id FROM subscriptions WHERE unique_code='SUB003'),
 'FAILED', NULL, 4, NULL, 'Connection refused', 0),

('EVT039', false, '2024-03-18 14:00:00', '2024-03-18 14:00:06', 'EVT039', 'CREDIT_DEPOSIT',         'Investment return credited for $1,850.00',
 (SELECT id FROM clients WHERE unique_code='CLIENT003'), (SELECT id FROM subscriptions WHERE unique_code='SUB003'),
 'DELIVERED', '2024-03-18 14:00:06', 0, NULL, NULL, 0),

('EVT040', false, '2024-03-18 16:30:00', '2024-03-18 16:30:00', 'EVT040', 'CREDIT_CARD_PAYMENT',    'Credit card payment received for $560.00',
 (SELECT id FROM clients WHERE unique_code='CLIENT003'), (SELECT id FROM subscriptions WHERE unique_code='SUB003'),
 'PENDING', NULL, 0, NULL, NULL, 0);
