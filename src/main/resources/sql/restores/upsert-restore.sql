INSERT INTO player_restores(
    player_id, restore_version, payload, state, attempts,
    available_at_epoch_ms, updated_at_epoch_ms
) VALUES (?, 1, ?, 'PENDING', 0, ?, ?)
ON CONFLICT(player_id) DO UPDATE SET
    payload = excluded.payload,
    state = 'PENDING',
    attempts = 0,
    available_at_epoch_ms = excluded.available_at_epoch_ms,
    updated_at_epoch_ms = excluded.updated_at_epoch_ms
