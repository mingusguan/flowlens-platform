drop table if exists sys_role_menu;
drop table if exists sys_user_role;
drop table if exists live_event_raw_202606;
drop table if exists live_event_202606;
drop table if exists live_user_gift_stat;
drop table if exists live_session_stat;
drop table if exists live_event_raw;
drop table if exists live_collector_task;
drop table if exists live_event;
drop table if exists live_session;
drop table if exists live_anchor;
drop table if exists sys_menu;
drop table if exists sys_role;
drop table if exists sys_user;

create table sys_user (
    id bigint not null auto_increment comment '用户ID',
    username varchar(64) not null comment '登录账号',
    password varchar(120) not null comment '登录密码',
    nickname varchar(64) not null comment '用户昵称',
    email varchar(120) comment '邮箱地址',
    mobile varchar(32) comment '手机号码',
    status tinyint not null default 1 comment '用户状态：1启用，0停用',
    create_time datetime not null default current_timestamp comment '创建时间',
    update_time datetime not null default current_timestamp on update current_timestamp comment '更新时间',
    primary key (id),
    unique key uk_sys_user_username (username)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='系统用户表';

create table sys_role (
    id bigint not null auto_increment comment '角色ID',
    role_name varchar(64) not null comment '角色名称',
    role_key varchar(64) not null comment '角色标识',
    status tinyint not null default 1 comment '角色状态：1启用，0停用',
    create_time datetime not null default current_timestamp comment '创建时间',
    update_time datetime not null default current_timestamp on update current_timestamp comment '更新时间',
    primary key (id),
    unique key uk_sys_role_role_key (role_key)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='系统角色表';

create table sys_menu (
    id bigint not null auto_increment comment '菜单ID',
    parent_id bigint not null default 0 comment '父级菜单ID，0表示根节点',
    menu_name varchar(64) not null comment '菜单名称',
    menu_type varchar(16) not null comment '菜单类型：CATALOG目录，MENU菜单，BUTTON按钮',
    path varchar(160) comment '前端路由路径',
    component varchar(160) comment '前端组件名称',
    permission varchar(160) comment '权限标识',
    icon varchar(64) comment '菜单图标',
    sort_order int not null default 0 comment '排序值',
    visible tinyint not null default 1 comment '是否显示：1显示，0隐藏',
    create_time datetime not null default current_timestamp comment '创建时间',
    update_time datetime not null default current_timestamp on update current_timestamp comment '更新时间',
    primary key (id),
    key idx_sys_menu_parent_id (parent_id),
    key idx_sys_menu_permission (permission)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='系统菜单表';

create table sys_user_role (
    id bigint not null auto_increment comment '用户角色关系ID',
    user_id bigint not null comment '用户ID',
    role_id bigint not null comment '角色ID',
    primary key (id),
    unique key uk_sys_user_role_user_role (user_id, role_id),
    key idx_sys_user_role_role_id (role_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='用户角色关联表';

create table sys_role_menu (
    id bigint not null auto_increment comment '角色菜单关系ID',
    role_id bigint not null comment '角色ID',
    menu_id bigint not null comment '菜单ID',
    primary key (id),
    unique key uk_sys_role_menu_role_menu (role_id, menu_id),
    key idx_sys_role_menu_menu_id (menu_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='角色菜单关联表';

create table live_anchor (
    id bigint not null auto_increment comment '主播ID',
    anchor_name varchar(80) not null comment '主播名称',
    douyin_live_id varchar(80) comment '抖音直播间ID',
    report_token varchar(80) not null comment '客户端上报密钥',
    status tinyint not null default 1 comment '主播状态：1启用，0停用',
    cloud_collect_enabled tinyint not null default 1 comment '是否启用云端兜底采集：1启用，0停用',
    client_online tinyint not null default 0 comment '客户端是否在线：1在线，0离线',
    client_instance_id varchar(120) comment '客户端实例ID',
    client_version varchar(64) comment '客户端版本号',
    client_last_heartbeat_time datetime comment '客户端最后心跳时间',
    cloud_collecting tinyint not null default 0 comment '云端是否正在采集：1是，0否',
    create_time datetime not null default current_timestamp comment '创建时间',
    update_time datetime not null default current_timestamp on update current_timestamp comment '更新时间',
    primary key (id),
    unique key uk_live_anchor_report_token (report_token),
    key idx_live_anchor_live_id (douyin_live_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='直播主播表';

create table live_session (
    id bigint not null auto_increment comment '直播场次ID',
    anchor_id bigint not null comment '主播ID',
    live_id varchar(80) comment '直播间ID',
    room_id varchar(80) comment '房间ID',
    live_title varchar(160) comment '直播标题',
    status varchar(24) not null comment '场次状态：LIVE直播中，ENDED已结束',
    active_source varchar(24) not null comment '当前采集来源：CLIENT客户端，CLOUD云端，NONE无',
    cloud_task_id bigint comment '当前云端采集任务ID',
    start_time datetime not null comment '开播时间',
    end_time datetime comment '结束时间',
    create_time datetime not null default current_timestamp comment '创建时间',
    update_time datetime not null default current_timestamp on update current_timestamp comment '更新时间',
    primary key (id),
    key idx_live_session_anchor_status (anchor_id, status),
    key idx_live_session_room_status (room_id, status),
    key idx_live_session_start_time (start_time)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='直播场次表';

create table live_event (
    id bigint not null auto_increment comment '直播事件ID',
    session_id bigint not null comment '直播场次ID',
    anchor_id bigint not null comment '主播ID',
    room_id varchar(80) comment '房间ID',
    live_id varchar(80) comment '直播间ID',
    msg_id varchar(120) comment '平台消息ID',
    event_type varchar(32) not null comment '事件类型：GIFT礼物，COMMENT评论，LIKE点赞，LIVE_END下播，MEMBER进房，FOLLOW关注，ROOM_STATS直播间统计',
    source varchar(24) not null comment '事件来源：CLIENT客户端，CLOUD云端',
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

create table live_event_raw (
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
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='直播事件原始报文模板表';

create table live_event_202606 like live_event;

create table live_event_raw_202606 like live_event_raw;

create table live_session_stat (
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

create table live_user_gift_stat (
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

create table live_collector_task (
    id bigint not null auto_increment comment '采集任务ID',
    session_id bigint not null comment '直播场次ID',
    anchor_id bigint not null comment '主播ID',
    live_id varchar(80) comment '直播间ID',
    room_id varchar(80) comment '房间ID',
    status varchar(24) not null comment '任务状态：STARTING启动中，RUNNING运行中，STOPPED已停止，FAILED失败',
    process_id bigint comment '采集进程ID',
    command_line varchar(1000) comment '采集启动命令',
    last_error varchar(1000) comment '最近一次错误信息',
    start_time datetime comment '任务启动时间',
    stop_time datetime comment '任务停止时间',
    create_time datetime not null default current_timestamp comment '创建时间',
    update_time datetime not null default current_timestamp on update current_timestamp comment '更新时间',
    primary key (id),
    key idx_live_collector_session_status (session_id, status),
    key idx_live_collector_anchor_status (anchor_id, status)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='云端采集任务表';
