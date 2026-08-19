UPDATE reward_outbox SET state='APPLIED', applied_at_epoch_ms=?, lease_until_epoch_ms=NULL
WHERE match_id=? AND player_id=? AND reward_type=? AND state='CLAIMED'
