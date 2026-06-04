alter table live_event
    add column viewer_count bigint not null default 0 comment '累计观看人数快照' after like_count;

alter table live_event_202606
    add column viewer_count bigint not null default 0 comment '累计观看人数快照' after like_count;

alter table live_session_stat
    add column viewer_count bigint not null default 0 comment '累计观看人数' after gift_value;

insert into live_session_stat (
    session_id,
    anchor_id,
    comment_count,
    like_count,
    gift_count,
    gift_value,
    viewer_count,
    create_time,
    update_time
)
select
    session_id,
    min(anchor_id) as anchor_id,
    sum(case when event_type = 'COMMENT' then 1 else 0 end) as comment_count,
    sum(case when event_type = 'LIKE' then like_count else 0 end) as like_count,
    sum(case when event_type = 'GIFT' then gift_count else 0 end) as gift_count,
    sum(case when event_type = 'GIFT' then gift_value else 0 end) as gift_value,
    max(case when event_type = 'ROOM_STATS' then viewer_count else 0 end) as viewer_count,
    min(create_time) as create_time,
    now() as update_time
from live_event_202606
group by session_id
on duplicate key update
    anchor_id = values(anchor_id),
    comment_count = values(comment_count),
    like_count = values(like_count),
    gift_count = values(gift_count),
    gift_value = values(gift_value),
    viewer_count = greatest(live_session_stat.viewer_count, values(viewer_count)),
    update_time = values(update_time);
