-- The original demo also arbitrarily suspended the first-pistol fixture.
UPDATE arena_markets SET status='OPEN', status_reason=NULL
WHERE status='SUSPENDED' AND template_code='PISTOL1'
AND event_id IN (SELECT id FROM arena_events WHERE external_key='demo-vct-open' AND demo=true AND status IN ('SCHEDULED','OPEN_FOR_PREDICTIONS'))
AND NOT EXISTS (SELECT 1 FROM arena_admin_audit_events a
    WHERE a.resource_type='MARKET' AND a.resource_id=CAST(arena_markets.id AS VARCHAR)
    AND a.action IN ('MARKET_STATUS_CHANGED','MARKET_UPDATED'));
