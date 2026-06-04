alter table live_anchor
    drop index idx_live_anchor_room_id;

alter table live_anchor
    drop column room_id;
