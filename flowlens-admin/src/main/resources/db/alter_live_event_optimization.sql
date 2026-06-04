create table if not exists live_event_raw (
    id bigint not null auto_increment comment '直播事件原始报文ID',
    event_id bigint not null comment '直播事件ID',
    session_id bigint not null comment '直播场次ID',
    anchor_id bigint not null comment '主播ID',
    raw_payload longtext not null comment '原始上报内容',
    create_time datetime not null default current_timestamp comment '创建时间',
    primary key (id),
    unique key uk_live_event_raw_event (event_id),
    key idx_live_event_raw_session (session_id),
    key idx_live_event_raw_anchor_time (anchor_id, create_time)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='直播事件原始报文表';

create table if not exists live_session_stat (
    id bigint not null auto_increment comment '直播场次统计ID',
    session_id bigint not null comment '直播场次ID',
    anchor_id bigint not null comment '主播ID',
    comment_count bigint not null default 0 comment '评论事件数量',
    like_count bigint not null default 0 comment '累计点赞数量',
    gift_count bigint not null default 0 comment '累计礼物数量',
    gift_value bigint not null default 0 comment '累计礼物价值',
    viewer_count bigint not null default 0 comment '累计观看人数',
    create_time datetime not null default current_timestamp comment '创建时间',
    update_time datetime not null default current_timestamp on update current_timestamp comment '更新时间',
    primary key (id),
    unique key uk_live_session_stat_session (session_id),
    key idx_live_session_stat_anchor (anchor_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='直播场次统计表';

create table if not exists live_user_gift_stat (
    id bigint not null auto_increment comment '直播用户礼物统计ID',
    session_id bigint not null comment '直播场次ID',
    anchor_id bigint not null comment '主播ID',
    user_key varchar(160) not null comment '观众聚合键，优先用户身份，私密用户按消息或事件隔离',
    user_id varchar(80) comment '观众用户ID',
    douyin_account varchar(120) comment '观众可搜索的抖音号',
    nickname varchar(120) comment '观众昵称',
    gift_count bigint not null default 0 comment '累计礼物数量',
    gift_value bigint not null default 0 comment '累计礼物价值',
    gift_event_count bigint not null default 0 comment '礼物事件次数',
    create_time datetime not null default current_timestamp comment '创建时间',
    update_time datetime not null default current_timestamp on update current_timestamp comment '更新时间',
    primary key (id),
    unique key uk_live_user_gift_session_user (session_id, user_key),
    key idx_live_user_gift_session_value (session_id, gift_value),
    key idx_live_user_gift_douyin_account (douyin_account)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='直播用户礼物统计表';

insert ignore into live_event_raw (event_id, session_id, anchor_id, raw_payload, create_time)
select id, session_id, anchor_id, raw_payload, create_time
from live_event
where raw_payload is not null and raw_payload <> '';

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
from live_event
group by session_id
on duplicate key update
    anchor_id = values(anchor_id),
    comment_count = values(comment_count),
    like_count = values(like_count),
    gift_count = values(gift_count),
    gift_value = values(gift_value),
    viewer_count = values(viewer_count),
    update_time = values(update_time);

insert into live_user_gift_stat (
    session_id,
    anchor_id,
    user_key,
    user_id,
    douyin_account,
    nickname,
    gift_count,
    gift_value,
    gift_event_count,
    create_time,
    update_time
)
select
    session_id,
    min(anchor_id) as anchor_id,
    coalesce(nullif(user_id, ''), nullif(douyin_account, ''), nullif(nickname, ''), concat('anonymous-event:', id)) as user_key,
    max(nullif(user_id, '')) as user_id,
    max(nullif(douyin_account, '')) as douyin_account,
    max(nullif(nickname, '')) as nickname,
    sum(gift_count) as gift_count,
    sum(gift_value) as gift_value,
    count(1) as gift_event_count,
    min(create_time) as create_time,
    now() as update_time
from live_event
where event_type = 'GIFT'
group by session_id, coalesce(nullif(user_id, ''), nullif(douyin_account, ''), nullif(nickname, ''), concat('anonymous-event:', id))
on duplicate key update
    anchor_id = values(anchor_id),
    user_id = values(user_id),
    douyin_account = values(douyin_account),
    nickname = values(nickname),
    gift_count = values(gift_count),
    gift_value = values(gift_value),
    gift_event_count = values(gift_event_count),
    update_time = values(update_time);

alter table live_event
    add index idx_live_event_douyin_account (douyin_account);

alter table live_event
    drop column raw_payload;
