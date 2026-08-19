SELECT amount,state,attempts,lease_until_epoch_ms FROM reward_outbox
WHERE match_id=? AND player_id=? AND reward_type=?
