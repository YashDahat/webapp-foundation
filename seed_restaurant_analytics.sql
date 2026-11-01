-- Demo dataset: "Spice Route" restaurant group (3 Bangalore outlets), ~18 months of orders.
-- Drives the admin Analytics dashboard. Orders use synthetic user_id (no FK). Re-runnable: it clears
-- its own SEED-% rows and 'spice-route-*' branches first.
BEGIN;

-- 0) clean any prior run of this seed
DELETE FROM daily_business_log
 WHERE log_date BETWEEN DATE '2025-04-01' AND DATE '2026-10-31';
DELETE FROM orders WHERE order_number LIKE 'SEED-%';
DELETE FROM branch WHERE slug LIKE 'spice-route-%';

-- 1) three restaurant outlets
INSERT INTO branch (name, slug, phone, address_line, city, state, pincode, opening_hours, active, created_at, updated_at)
VALUES
  ('Spice Route — Koramangala', 'spice-route-koramangala', '+918040000001', '80 Ft Road, 6th Block', 'Bengaluru', 'Karnataka', '560095', '11:00–23:00', true, now(), now()),
  ('Spice Route — Indiranagar',  'spice-route-indiranagar',  '+918040000002', '100 Ft Road',            'Bengaluru', 'Karnataka', '560038', '11:00–23:00', true, now(), now()),
  ('Spice Route — HSR Layout',   'spice-route-hsr',          '+918040000003', '27th Main, Sector 2',    'Bengaluru', 'Karnataka', '560102', '11:00–23:00', true, now(), now());

-- 2) orders — one row per bill, weekday-seasonal volume + gentle YoY growth + noise
WITH br AS (
  SELECT array_agg(id ORDER BY id) AS ids, array_agg(name ORDER BY id) AS names
  FROM branch WHERE slug LIKE 'spice-route-%'
),
days AS (
  SELECT d::date AS day,
         ((d::date - DATE '2025-04-01')::float / 578.0) AS prog
  FROM generate_series(DATE '2025-04-01', DATE '2026-10-31', INTERVAL '1 day') d
),
gen AS (
  SELECT
    days.day,
    1 + floor(random() * 3)::int AS bidx,
    -- customer pool grows 500 -> ~12500 over the span; power() biases to older ids (repeats),
    -- newest ids only reachable later (natural new-customer stream)
    1 + floor(power(random(), 1.5) * (500 + (12000 * days.prog)))::int AS user_id,
    round(250 + power(random(), 1.3) * 1600)::numeric(38,2) AS tot,
    ((days.day::timestamp + interval '11 hours' + (random() * 39600)::int * interval '1 second')
       AT TIME ZONE 'Asia/Kolkata') AS ts,
    CASE WHEN random() < 0.03 THEN 'REFUNDED'
         WHEN random() < 0.04 THEN 'REFUND_PENDING'
         ELSE 'PAID' END AS pstate,
    CASE WHEN random() < 0.7 THEN 'SELF_PICKUP' ELSE 'DELIVERY' END AS ftype
  FROM days
  CROSS JOIN LATERAL generate_series(1,
    GREATEST(8, round(90
      * CASE EXTRACT(dow FROM days.day) WHEN 0 THEN 1.4 WHEN 5 THEN 1.15 WHEN 6 THEN 1.5 ELSE 1.0 END
      * (1 + 0.15 * days.prog)
      * (0.85 + 0.30 * random()))::int)
  ) gs
)
INSERT INTO orders (user_id, order_number, reference_id, branch_id, branch_name,
  fulfillment_type, payment_mode, workflow_code, current_stage_code, payment_state,
  subtotal, total, refund_amount, currency, created_at, updated_at, version, blocked, carried_forward, channel)
SELECT
  gen.user_id,
  'SEED-' || row_number() OVER (),
  'seed_' || row_number() OVER (),
  br.ids[gen.bidx],
  br.names[gen.bidx],
  gen.ftype,
  'ONLINE',
  'food',
  'served',
  gen.pstate,
  gen.tot,
  gen.tot,
  CASE WHEN gen.pstate IN ('REFUNDED', 'REFUND_PENDING') THEN gen.tot ELSE 0 END,
  'INR',
  gen.ts, gen.ts, 0, false, false, 'DINE_IN'
FROM gen CROSS JOIN br;

-- 3) end-of-day log (COGS / operating expenses / complaints) derived from each day's actual sales
INSERT INTO daily_business_log (log_date, cogs, operating_expenses, complaints, created_at, updated_at)
SELECT s.day,
       round((s.net * (0.31 + random() * 0.04))::numeric, 2),   -- food cost ~31–35%
       round((s.net * (0.50 + random() * 0.04))::numeric, 2),   -- rent + staff + other ~50–54%  (=> net margin ~13–17%)
       floor(random() * 3)::int,                     -- 0–2 complaints/day
       now(), now()
FROM (
  SELECT (created_at AT TIME ZONE 'Asia/Kolkata')::date AS day,
         sum(total - coalesce(refund_amount, 0)) AS net
  FROM orders
  WHERE payment_state IN ('PAID', 'REFUND_PENDING', 'REFUNDED')
    AND order_number LIKE 'SEED-%'
  GROUP BY 1
) s
ON CONFLICT (log_date) DO NOTHING;

COMMIT;

-- summary
SELECT 'orders'      AS what, count(*)::text AS n, min(created_at)::date::text AS a, max(created_at)::date::text AS b FROM orders WHERE order_number LIKE 'SEED-%'
UNION ALL
SELECT 'net sales ₹', to_char(sum(total - coalesce(refund_amount,0)),'FM999,99,99,999'), NULL, NULL FROM orders WHERE order_number LIKE 'SEED-%' AND payment_state IN ('PAID','REFUND_PENDING','REFUNDED')
UNION ALL
SELECT 'daily logs', count(*)::text, min(log_date)::text, max(log_date)::text FROM daily_business_log
UNION ALL
SELECT 'branches', count(*)::text, NULL, NULL FROM branch WHERE slug LIKE 'spice-route-%';
