UPDATE player_restores
SET state = ?,
    completed_at_epoch_ms = ?,
    updated_at_epoch_ms = ?
WHERE player_id = ?
  AND state = 'CLAIMED'
