ALTER TABLE reward_outbox RENAME TO reward_outbox_v1;

CREATE TABLE reward_outbox (
    match_id TEXT NOT NULL,
    player_id TEXT NOT NULL,
    reward_type TEXT NOT NULL,
    amount INTEGER NOT NULL CHECK (amount >= 0),
    state TEXT NOT NULL CHECK (state IN ('PENDING', 'CLAIMED', 'APPLIED', 'ABORTED')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    created_at_epoch_ms INTEGER NOT NULL,
    claimed_at_epoch_ms INTEGER,
    lease_until_epoch_ms INTEGER,
    applied_at_epoch_ms INTEGER,
    PRIMARY KEY (match_id, player_id, reward_type)
);

INSERT INTO reward_outbox (
    match_id, player_id, reward_type, amount, state, attempts,
    created_at_epoch_ms, claimed_at_epoch_ms, lease_until_epoch_ms, applied_at_epoch_ms
)
SELECT
    match_id, player_id, reward_type, amount, state, 0,
    created_at_epoch_ms, NULL, NULL, applied_at_epoch_ms
FROM reward_outbox_v1;

DROP TABLE reward_outbox_v1;

CREATE INDEX idx_reward_outbox_pending
    ON reward_outbox (state, created_at_epoch_ms);
CREATE INDEX idx_reward_outbox_claimable
    ON reward_outbox (state, lease_until_epoch_ms, created_at_epoch_ms);
