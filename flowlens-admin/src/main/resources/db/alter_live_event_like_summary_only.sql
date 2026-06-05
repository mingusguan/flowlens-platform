-- LIKE events are summary-only now. Keep live_session_stat.like_count, but remove
-- historical LIKE detail rows from the live_event tables.

delete from live_event
where event_type = 'LIKE';

delete from live_event_202606
where event_type = 'LIKE';
