-- I-01: Inbox quick capture (docs/DATA_MODEL.md inbox_items). Free text only; nothing is inferred from it.
-- Triage columns exist now so I-02 needs no table rebuild, but CHECKs guarantee an unprocessed item carries no
-- decision and no reference to a created Goal/Task/Focus Item.
CREATE TABLE inbox_items (
    id                  TEXT PRIMARY KEY NOT NULL CHECK (length(id) = 36),
    -- stored exactly as typed (outer whitespace trimmed by the API); 1..4000 characters
    raw_text            TEXT NOT NULL CHECK (typeof(raw_text) = 'text' AND length(trim(raw_text)) > 0 AND length(raw_text) <= 4000),
    status              TEXT NOT NULL DEFAULT 'unprocessed' CHECK (status IN ('unprocessed', 'processed')),
    decision            TEXT CHECK (decision IS NULL OR decision IN ('task', 'goal', 'focus_item', 'reference', 'someday', 'discard')),
    created_entity_type TEXT CHECK (created_entity_type IS NULL OR created_entity_type IN ('task', 'goal', 'focus_item')),
    created_entity_id   TEXT CHECK (created_entity_id IS NULL OR length(created_entity_id) = 36),
    processed_at        TEXT CHECK (processed_at IS NULL OR processed_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z'),
    captured_at         TEXT NOT NULL CHECK (captured_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z'),
    created_at          TEXT NOT NULL CHECK (created_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z'),
    updated_at          TEXT NOT NULL CHECK (updated_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z'),
    -- unprocessed ⇔ no decision, no processed_at, no created entity
    CHECK ((status = 'unprocessed') = (decision IS NULL AND processed_at IS NULL)),
    CHECK (status = 'processed' OR (created_entity_type IS NULL AND created_entity_id IS NULL)),
    CHECK ((created_entity_type IS NULL) = (created_entity_id IS NULL))
);
CREATE INDEX idx_inbox_items_status_captured ON inbox_items (status, captured_at);
