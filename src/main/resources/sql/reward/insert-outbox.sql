INSERT INTO reward_outbox(match_id,player_id,reward_type,amount,state,created_at_epoch_ms)
VALUES (?,?,?,?,'PENDING',?) ON CONFLICT(match_id,player_id,reward_type) DO NOTHING
