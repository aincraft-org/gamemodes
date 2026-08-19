UPDATE reward_outbox SET state='CLAIMED', attempts=attempts+1,
claimed_at_epoch_ms=?, lease_until_epoch_ms=? WHERE match_id=? AND player_id=?
AND reward_type=? AND (state='PENDING' OR (state='CLAIMED' AND lease_until_epoch_ms<=?))
