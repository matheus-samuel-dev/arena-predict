-- The controlled scenario is a separate, explicitly owned internal competition.
ALTER TABLE arena_championships ADD COLUMN demo_managed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE arena_championships ADD CONSTRAINT ck_championship_demo_managed
    CHECK (demo_managed = FALSE OR external_provider IS NULL);
ALTER TABLE arena_championships ADD CONSTRAINT uq_championship_demo_scope UNIQUE(id, demo_managed);
ALTER TABLE arena_events ADD COLUMN demo_managed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE arena_events ADD COLUMN demo_archived BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE arena_events ADD CONSTRAINT ck_event_demo_scope
    CHECK ((demo_managed = FALSE AND demo_archived = FALSE) OR (demo = TRUE AND external_provider IS NULL));
ALTER TABLE arena_events ADD CONSTRAINT fk_event_demo_scope
    FOREIGN KEY (championship_id, demo_managed) REFERENCES arena_championships(id, demo_managed);
ALTER TABLE arena_events ADD CONSTRAINT uq_event_demo_scope UNIQUE(id, demo_managed);

CREATE TABLE arena_demo_scenario (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    generation BIGINT NOT NULL DEFAULT 0 CHECK (generation >= 0),
    demo_managed BOOLEAN NOT NULL DEFAULT TRUE CHECK (demo_managed = TRUE),
    championship_id BIGINT,
    active_event_id BIGINT,
    history_event_id BIGINT,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (championship_id, demo_managed) REFERENCES arena_championships(id, demo_managed),
    FOREIGN KEY (active_event_id, demo_managed) REFERENCES arena_events(id, demo_managed),
    FOREIGN KEY (history_event_id, demo_managed) REFERENCES arena_events(id, demo_managed)
);
INSERT INTO arena_demo_scenario(id) VALUES (1);
