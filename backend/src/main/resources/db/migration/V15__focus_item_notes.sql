-- Notes of a focus item (owner request 2026-11-12): free text kept with a focus item, distinct from tasks (no status,
-- no planning, no progress). Owned through focus_items (no user_id of its own). Forward-only.
CREATE TABLE focus_item_notes (
    id            TEXT PRIMARY KEY NOT NULL CHECK (length(id) = 36),
    focus_item_id TEXT NOT NULL REFERENCES focus_items (id),
    body          TEXT NOT NULL CHECK (length(trim(body)) > 0),
    created_at    TEXT NOT NULL CHECK (created_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z'),
    updated_at    TEXT NOT NULL CHECK (updated_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z')
);
CREATE INDEX idx_focus_item_notes_item ON focus_item_notes (focus_item_id, created_at);
