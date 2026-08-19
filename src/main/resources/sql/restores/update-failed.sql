UPDATE player_restores
SET state = 'FAILED',
    last_error = ?,
    updated_at_epoch_ms = ?
WHERE player_id = ?
  AND state = 'CLAIMED'
