SELECT match_id,player_id,reward_type FROM reward_outbox
WHERE state='PENDING' OR (state='CLAIMED' AND lease_until_epoch_ms<=?)
ORDER BY created_at_epoch_ms,match_id,player_id,reward_type LIMIT ?
