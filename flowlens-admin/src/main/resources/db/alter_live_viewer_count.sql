alter table live_event
    add column viewer_count bigint not null default 0 comment '累计观看人数快照' after like_count;

alter table live_event_202606
    add column viewer_count bigint not null default 0 comment '累计观看人数快照' after like_count;

alter table live_session_stat
    add column viewer_count bigint not null default 0 comment '累计观看人数' after gift_value;
