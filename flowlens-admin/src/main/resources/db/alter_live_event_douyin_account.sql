alter table live_event
    add column douyin_account varchar(120) null comment '观众可搜索的抖音号' after user_id;
