UPDATE player_restores
SET state = 'CLAIMED',
    attempts = attempts + 1,
    claimed_at_epoch_ms = ?,
    updated_at_epoch_ms = ?
WHERE player_id = ?
  AND state IN ('PENDING', 'FAILED')
  AND available_at_epoch_ms <= ?
