insert into sys_user (id, username, password, nickname, email, mobile, status, create_time, update_time)
values (1, 'admin', '{noop}admin123', '平台管理员', 'admin@flowlens.local', '13800000000', 1, current_timestamp, current_timestamp);

insert into sys_role (id, role_name, role_key, status, create_time, update_time)
values (1, '超级管理员', 'admin', 1, current_timestamp, current_timestamp),
       (2, '运营分析师', 'analyst', 1, current_timestamp, current_timestamp);

insert into sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, visible, create_time, update_time)
values (100, 0, '工作台', 'MENU', '/dashboard', 'Dashboard', 'dashboard:view', 'Gauge', 1, 1, current_timestamp, current_timestamp),
       (200, 0, '系统管理', 'CATALOG', '/system', 'Layout', 'system:view', 'Settings', 90, 1, current_timestamp, current_timestamp),
       (210, 200, '用户管理', 'MENU', '/system/users', 'SystemUsers', 'system:user:list', 'Users', 1, 1, current_timestamp, current_timestamp),
       (211, 210, '保存用户', 'BUTTON', null, null, 'system:user:save', null, 1, 0, current_timestamp, current_timestamp),
       (212, 210, '删除用户', 'BUTTON', null, null, 'system:user:delete', null, 2, 0, current_timestamp, current_timestamp),
       (220, 200, '角色管理', 'MENU', '/system/roles', 'SystemRoles', 'system:role:list', 'ShieldCheck', 2, 1, current_timestamp, current_timestamp),
       (221, 220, '保存角色', 'BUTTON', null, null, 'system:role:save', null, 1, 0, current_timestamp, current_timestamp),
       (222, 220, '删除角色', 'BUTTON', null, null, 'system:role:delete', null, 2, 0, current_timestamp, current_timestamp),
       (230, 200, '权限管理', 'MENU', '/system/menus', 'SystemMenus', 'system:menu:list', 'KeyRound', 3, 1, current_timestamp, current_timestamp),
       (231, 230, '保存权限', 'BUTTON', null, null, 'system:menu:save', null, 1, 0, current_timestamp, current_timestamp),
       (232, 230, '删除权限', 'BUTTON', null, null, 'system:menu:delete', null, 2, 0, current_timestamp, current_timestamp),
       (300, 0, '直播值班', 'CATALOG', '/live', 'Layout', 'live:view', 'RadioTower', 20, 1, current_timestamp, current_timestamp),
       (310, 300, '主播值班台', 'MENU', '/live/anchors', 'LiveAnchors', 'live:anchor:list', 'Podcast', 1, 1, current_timestamp, current_timestamp),
       (311, 310, '保存主播', 'BUTTON', null, null, 'live:anchor:save', null, 1, 0, current_timestamp, current_timestamp),
       (312, 310, '删除主播', 'BUTTON', null, null, 'live:anchor:delete', null, 2, 0, current_timestamp, current_timestamp),
       (313, 310, '控制直播场次', 'BUTTON', null, null, 'live:session:control', null, 3, 0, current_timestamp, current_timestamp),
       (314, 310, '查看直播场次', 'BUTTON', null, null, 'live:session:list', null, 4, 0, current_timestamp, current_timestamp);

insert into sys_user_role (id, user_id, role_id)
values (1, 1, 1);

insert into sys_role_menu (id, role_id, menu_id)
values (1001, 1, 100),
       (1002, 1, 200),
       (1003, 1, 210),
       (1004, 1, 211),
       (1005, 1, 212),
       (1006, 1, 220),
       (1007, 1, 221),
       (1008, 1, 222),
       (1009, 1, 230),
       (1010, 1, 231),
       (1011, 1, 232),
       (1012, 1, 300),
       (1013, 1, 310),
       (1014, 1, 311),
       (1015, 1, 312),
       (1016, 1, 313),
       (1017, 1, 314),
       (2001, 2, 100);
