UPDATE reward_outbox SET state='ABORTED', lease_until_epoch_ms=NULL WHERE match_id=?
AND state IN ('PENDING','CLAIMED')
