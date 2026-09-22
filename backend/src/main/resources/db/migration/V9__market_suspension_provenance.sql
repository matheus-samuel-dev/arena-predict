ALTER TABLE arena_markets ADD COLUMN status_reason VARCHAR(500);

-- Repair only the arbitrary old demo suspension, never override an audited action.
UPDATE arena_markets SET status='OPEN'
WHERE status='SUSPENDED' AND template_code='TOTAL_CORNERS'
AND event_id IN (SELECT id FROM arena_events WHERE external_key='demo-football-live' AND demo=true AND status='LIVE')
AND NOT EXISTS (SELECT 1 FROM arena_admin_audit_events a
    WHERE a.resource_type='MARKET' AND a.resource_id=CAST(arena_markets.id AS VARCHAR)
    AND a.action IN ('MARKET_STATUS_CHANGED','MARKET_UPDATED'));

UPDATE arena_markets SET status_reason='Suspensão administrativa registrada.'
WHERE status='SUSPENDED' AND EXISTS (SELECT 1 FROM arena_admin_audit_events a
    WHERE a.resource_type='MARKET' AND a.resource_id=CAST(arena_markets.id AS VARCHAR)
    AND a.action IN ('MARKET_STATUS_CHANGED','MARKET_UPDATED'));
