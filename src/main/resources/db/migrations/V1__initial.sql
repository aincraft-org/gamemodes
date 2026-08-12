CREATE TABLE IF NOT EXISTS schema_migrations (
    version INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    applied_at_epoch_ms INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS match_snapshots (
    match_id TEXT PRIMARY KEY NOT NULL,
    sequence INTEGER NOT NULL CHECK (sequence >= 0),
    snapshot_version INTEGER NOT NULL CHECK (snapshot_version > 0),
    payload BLOB NOT NULL,
    deadline_epoch_ms INTEGER,
    updated_at_epoch_ms INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS player_restores (
    player_id TEXT PRIMARY KEY NOT NULL,
    restore_version INTEGER NOT NULL CHECK (restore_version > 0),
    payload BLOB NOT NULL,
    state TEXT NOT NULL CHECK (state IN ('PENDING', 'CLAIMED', 'RESTORED', 'FAILED', 'CANCELLED')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    available_at_epoch_ms INTEGER NOT NULL,
    claimed_at_epoch_ms INTEGER,
    completed_at_epoch_ms INTEGER,
    last_error TEXT,
    updated_at_epoch_ms INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS reward_outbox (
    match_id TEXT NOT NULL,
    player_id TEXT NOT NULL,
    reward_type TEXT NOT NULL,
    amount INTEGER NOT NULL CHECK (amount >= 0),
    state TEXT NOT NULL CHECK (state IN ('PENDING', 'APPLIED', 'ABORTED')),
    created_at_epoch_ms INTEGER NOT NULL,
    applied_at_epoch_ms INTEGER,
    PRIMARY KEY (match_id, player_id, reward_type)
);

CREATE INDEX IF NOT EXISTS idx_player_restores_pending
    ON player_restores (state, available_at_epoch_ms);
CREATE INDEX IF NOT EXISTS idx_reward_outbox_pending
    ON reward_outbox (state, created_at_epoch_ms);
