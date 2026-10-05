-- Applied on every startup by Database.init; every statement must be safe to re-run.

CREATE TABLE IF NOT EXISTS config (
    name       text        PRIMARY KEY,
    data       jsonb       NOT NULL,
    version    int         NOT NULL DEFAULT 1,
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS config_history (
    id         bigserial   PRIMARY KEY,
    name       text        NOT NULL REFERENCES config (name),
    path       text,
    old_value  jsonb,
    new_value  jsonb,
    changed_by text        NOT NULL,
    changed_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS config_history_name_changed_at ON config_history (name, changed_at DESC);
