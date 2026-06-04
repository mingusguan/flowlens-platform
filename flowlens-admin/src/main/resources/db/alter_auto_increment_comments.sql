alter table sys_user
    modify column id bigint not null auto_increment comment '用户ID',
    modify column username varchar(64) not null comment '登录账号',
    modify column password varchar(120) not null comment '登录密码',
    modify column nickname varchar(64) not null comment '用户昵称',
    modify column email varchar(120) comment '邮箱地址',
    modify column mobile varchar(32) comment '手机号码',
    modify column status tinyint not null default 1 comment '用户状态：1启用，0停用',
    modify column create_time datetime not null default current_timestamp comment '创建时间',
    modify column update_time datetime not null default current_timestamp on update current_timestamp comment '更新时间',
    comment='系统用户表';

alter table sys_role
    modify column id bigint not null auto_increment comment '角色ID',
    modify column role_name varchar(64) not null comment '角色名称',
    modify column role_key varchar(64) not null comment '角色标识',
    modify column status tinyint not null default 1 comment '角色状态：1启用，0停用',
    modify column create_time datetime not null default current_timestamp comment '创建时间',
    modify column update_time datetime not null default current_timestamp on update current_timestamp comment '更新时间',
    comment='系统角色表';

alter table sys_menu
    modify column id bigint not null auto_increment comment '菜单ID',
    modify column parent_id bigint not null default 0 comment '父级菜单ID，0表示根节点',
    modify column menu_name varchar(64) not null comment '菜单名称',
    modify column menu_type varchar(16) not null comment '菜单类型：CATALOG目录，MENU菜单，BUTTON按钮',
    modify column path varchar(160) comment '前端路由路径',
    modify column component varchar(160) comment '前端组件名称',
    modify column permission varchar(160) comment '权限标识',
    modify column icon varchar(64) comment '菜单图标',
    modify column sort_order int not null default 0 comment '排序值',
    modify column visible tinyint not null default 1 comment '是否显示：1显示，0隐藏',
    modify column create_time datetime not null default current_timestamp comment '创建时间',
    modify column update_time datetime not null default current_timestamp on update current_timestamp comment '更新时间',
    comment='系统菜单表';

alter table sys_user_role
    modify column id bigint not null auto_increment comment '用户角色关系ID',
    modify column user_id bigint not null comment '用户ID',
    modify column role_id bigint not null comment '角色ID',
    comment='用户角色关联表';

alter table sys_role_menu
    modify column id bigint not null auto_increment comment '角色菜单关系ID',
    modify column role_id bigint not null comment '角色ID',
    modify column menu_id bigint not null comment '菜单ID',
    comment='角色菜单关联表';

alter table live_anchor
    modify column id bigint not null auto_increment comment '主播ID',
    modify column anchor_name varchar(80) not null comment '主播名称',
    modify column douyin_live_id varchar(80) comment '抖音直播间ID',
    modify column room_id varchar(80) comment '直播房间ID',
    modify column report_token varchar(80) not null comment '客户端上报密钥',
    modify column status tinyint not null default 1 comment '主播状态：1启用，0停用',
    modify column cloud_collect_enabled tinyint not null default 1 comment '是否启用云端兜底采集：1启用，0停用',
    modify column client_online tinyint not null default 0 comment '客户端是否在线：1在线，0离线',
    modify column client_instance_id varchar(120) comment '客户端实例ID',
    modify column client_version varchar(64) comment '客户端版本号',
    modify column client_last_heartbeat_time datetime comment '客户端最后心跳时间',
    modify column cloud_collecting tinyint not null default 0 comment '云端是否正在采集：1是，0否',
    modify column create_time datetime not null default current_timestamp comment '创建时间',
    modify column update_time datetime not null default current_timestamp on update current_timestamp comment '更新时间',
    comment='直播主播表';

alter table live_session
    modify column id bigint not null auto_increment comment '直播场次ID',
    modify column anchor_id bigint not null comment '主播ID',
    modify column live_id varchar(80) comment '直播间ID',
    modify column room_id varchar(80) comment '房间ID',
    modify column live_title varchar(160) comment '直播标题',
    modify column status varchar(24) not null comment '场次状态：LIVE直播中，ENDED已结束',
    modify column active_source varchar(24) not null comment '当前采集来源：CLIENT客户端，CLOUD云端，NONE无',
    modify column cloud_task_id bigint comment '当前云端采集任务ID',
    modify column start_time datetime not null comment '开播时间',
    modify column end_time datetime comment '结束时间',
    modify column create_time datetime not null default current_timestamp comment '创建时间',
    modify column update_time datetime not null default current_timestamp on update current_timestamp comment '更新时间',
    comment='直播场次表';

alter table live_event
    modify column id bigint not null auto_increment comment '直播事件ID',
    modify column session_id bigint not null comment '直播场次ID',
    modify column anchor_id bigint not null comment '主播ID',
    modify column room_id varchar(80) comment '房间ID',
    modify column live_id varchar(80) comment '直播间ID',
    modify column msg_id varchar(120) comment '平台消息ID',
    modify column event_type varchar(32) not null comment '事件类型：GIFT礼物，COMMENT评论，LIKE点赞，LIVE_END下播，MEMBER进房，FOLLOW关注，ROOM_STATS直播间统计',
    modify column source varchar(24) not null comment '事件来源：CLIENT客户端，CLOUD云端',
    modify column user_id varchar(80) comment '观众用户ID',
    modify column douyin_account varchar(120) comment '观众可搜索的抖音号',
    modify column nickname varchar(120) comment '观众昵称',
    modify column content varchar(1000) comment '评论或事件内容',
    modify column gift_id varchar(80) comment '礼物ID',
    modify column gift_name varchar(120) comment '礼物名称',
    modify column gift_count int not null default 0 comment '礼物数量',
    modify column gift_value bigint not null default 0 comment '礼物价值',
    modify column like_count bigint not null default 0 comment '点赞数量',
    modify column viewer_count bigint not null default 0 comment '累计观看人数快照',
    modify column event_time datetime not null comment '事件发生时间',
    modify column create_time datetime not null default current_timestamp comment '创建时间',
    comment='直播事件表';

alter table live_event_raw
    modify column id bigint not null auto_increment comment '直播事件原始报文ID',
    modify column event_id bigint not null comment '直播事件ID',
    modify column session_id bigint not null comment '直播场次ID',
    modify column anchor_id bigint not null comment '主播ID',
    modify column raw_payload longtext not null comment '原始上报内容',
    modify column create_time datetime not null default current_timestamp comment '创建时间',
    comment='直播事件原始报文表';

alter table live_session_stat
    modify column id bigint not null auto_increment comment '直播场次统计ID',
    modify column session_id bigint not null comment '直播场次ID',
    modify column anchor_id bigint not null comment '主播ID',
    modify column comment_count bigint not null default 0 comment '评论事件数量',
    modify column like_count bigint not null default 0 comment '累计点赞数量',
    modify column gift_count bigint not null default 0 comment '累计礼物数量',
    modify column gift_value bigint not null default 0 comment '累计礼物价值',
    modify column viewer_count bigint not null default 0 comment '累计观看人数',
    modify column create_time datetime not null default current_timestamp comment '创建时间',
    modify column update_time datetime not null default current_timestamp on update current_timestamp comment '更新时间',
    comment='直播场次统计表';

alter table live_user_gift_stat
    modify column id bigint not null auto_increment comment '直播用户礼物统计ID',
    modify column session_id bigint not null comment '直播场次ID',
    modify column anchor_id bigint not null comment '主播ID',
    modify column user_key varchar(160) not null comment '观众聚合键，优先用户身份，私密用户按消息或事件隔离',
    modify column user_id varchar(80) comment '观众用户ID',
    modify column douyin_account varchar(120) comment '观众可搜索的抖音号',
    modify column nickname varchar(120) comment '观众昵称',
    modify column gift_count bigint not null default 0 comment '累计礼物数量',
    modify column gift_value bigint not null default 0 comment '累计礼物价值',
    modify column gift_event_count bigint not null default 0 comment '礼物事件次数',
    modify column create_time datetime not null default current_timestamp comment '创建时间',
    modify column update_time datetime not null default current_timestamp on update current_timestamp comment '更新时间',
    comment='直播用户礼物统计表';

alter table live_collector_task
    modify column id bigint not null auto_increment comment '采集任务ID',
    modify column session_id bigint not null comment '直播场次ID',
    modify column anchor_id bigint not null comment '主播ID',
    modify column live_id varchar(80) comment '直播间ID',
    modify column room_id varchar(80) comment '房间ID',
    modify column status varchar(24) not null comment '任务状态：STARTING启动中，RUNNING运行中，STOPPED已停止，FAILED失败',
    modify column process_id bigint comment '采集进程ID',
    modify column command_line varchar(1000) comment '采集启动命令',
    modify column last_error varchar(1000) comment '最近一次错误信息',
    modify column start_time datetime comment '任务启动时间',
    modify column stop_time datetime comment '任务停止时间',
    modify column create_time datetime not null default current_timestamp comment '创建时间',
    modify column update_time datetime not null default current_timestamp on update current_timestamp comment '更新时间',
    comment='云端采集任务表';
