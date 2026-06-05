create table if not exists live_event (
    id bigint not null auto_increment comment '直播事件ID',
    session_id bigint not null comment '直播场次ID',
    anchor_id bigint not null comment '主播ID',
    room_id varchar(80) comment '房间ID',
    live_id varchar(80) comment '直播间ID',
    msg_id varchar(120) comment '平台消息ID',
    event_type varchar(32) not null comment '事件类型：GIFT礼物，COMMENT评论，LIKE点赞，LIVE_END下播，MEMBER进房，FOLLOW关注，ROOM_STATS直播间统计',
    source varchar(24) not null comment '事件来源：CLOUD云端',
    user_id varchar(80) comment '观众用户ID',
    douyin_account varchar(120) comment '观众可搜索的抖音号',
    nickname varchar(120) comment '观众昵称',
    content varchar(1000) comment '评论或事件内容',
    gift_id varchar(80) comment '礼物ID',
    gift_name varchar(120) comment '礼物名称',
    gift_count int not null default 0 comment '礼物数量',
    gift_value bigint not null default 0 comment '礼物价值',
    like_count bigint not null default 0 comment '点赞数量',
    viewer_count bigint not null default 0 comment '累计观看人数快照',
    event_time datetime not null comment '事件发生时间',
    create_time datetime not null default current_timestamp comment '创建时间',
    primary key (id),
    unique key uk_live_event_msg (room_id, event_type, msg_id),
    key idx_live_event_session_type (session_id, event_type),
    key idx_live_event_anchor_time (anchor_id, event_time),
    key idx_live_event_douyin_account (douyin_account)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='直播事件模板表';

create table if not exists live_event_202606 like live_event;
