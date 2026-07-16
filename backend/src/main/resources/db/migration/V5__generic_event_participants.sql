CREATE TABLE arena_event_participants (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL REFERENCES arena_events(id) ON DELETE CASCADE,
    competitor_id BIGINT NOT NULL REFERENCES arena_competitors(id),
    display_order INTEGER NOT NULL DEFAULT 0,
    position INTEGER,
    score_label VARCHAR(80),
    CONSTRAINT uk_arena_event_participant UNIQUE (event_id, competitor_id),
    CONSTRAINT chk_arena_event_participant_order CHECK (display_order >= 0),
    CONSTRAINT chk_arena_event_participant_position CHECK (position IS NULL OR position > 0)
);

CREATE INDEX idx_arena_event_participants_event ON arena_event_participants(event_id, display_order);
CREATE INDEX idx_arena_event_participants_competitor ON arena_event_participants(competitor_id);

INSERT INTO arena_event_participants(event_id, competitor_id, display_order)
SELECT event.id, event.home_competitor_id, 0
FROM arena_events event
WHERE event.home_competitor_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM arena_event_participants participant
      WHERE participant.event_id = event.id AND participant.competitor_id = event.home_competitor_id
  );

INSERT INTO arena_event_participants(event_id, competitor_id, display_order)
SELECT event.id, event.away_competitor_id, 1
FROM arena_events event
WHERE event.away_competitor_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM arena_event_participants participant
      WHERE participant.event_id = event.id AND participant.competitor_id = event.away_competitor_id
  );
