-- roomManager 数据表 创建时间： 2020/02/03


-- 用户信息表
create sequence user_info_uid_seq start with 10001;
create sequence user_info_rid_seq start with 20001;
create table user_info (
  uid bigint not null default user_info_uid_seq.nextval primary key ,
  user_name varchar(100) not null,
  password varchar(100) not null,
  roomId bigint not null default user_info_rid_seq.nextval,
  token varchar(63) not null default '',
  token_create_time bigint not null,
  head_img varchar(256) not null default '',
  email varchar(256) not null default '',
  role boolean not null default false,  -- 用户角色，1为主持人，0为参会者
  sealed BOOLEAN NOT NULL DEFAULT FALSE, -- 用户是否被封
  sealed_util_time BIGINT NOT NULL DEFAULT 0 -- 用户被封禁时间
);
create unique index USER_INFO_USER_NAME_uindex
on USER_INFO (USER_NAME);
alter table USER_INFO alter column UID BIGINT default NEXT VALUE FOR "PUBLIC"."USER_INFO_UID_SEQ" auto_increment;
alter table USER_INFO alter column ROOMID BIGINT default NEXT VALUE FOR "PUBLIC"."USER_INFO_RID_SEQ" auto_increment;

-- 录像表
create table record(
   id BIGSERIAL NOT NULL PRIMARY KEY,
   room_id bigint NOT NULL DEFAULT 0,
   start_time bigint NOT NULL DEFAULT 0,
   cover_img VARCHAR(256) NOT NULL DEFAULT '',
   record_name VARCHAR NOT NULL DEFAULT '',
   record_des VARCHAR NOT NULL DEFAULT '',
   view_num INTEGER NOT NULL DEFAULT 0,
   like_num INTEGER NOT NULL DEFAULT 0,
   duration VARCHAR (100) NOT NULL DEFAULT '',
   record_addr VARCHAR(100) NOT NULL DEFAULT ''
)

-- 录像评论统计表
CREATE TABLE record_comment(
  comment_id BIGSERIAL NOT NULL PRIMARY KEY,
  room_id bigint NOT NULL ,
  record_time bigint NOT NULL ,
  comment VARCHAR NOT NULL DEFAULT '',
  comment_time bigint NOT NULL ,
  comment_uid bigint NOT NULL ,
  author_uid bigint, --被评论的用户id,如果是None，就是回复主播
  relative_time BIGINT NOT NULL DEFAULT 0
);

--登录事件表
create sequence login_event_id_seq START WITH 30001;
create table login_event (
   id bigint default login_event_id_seq.nextval primary key ,
   uid bigint not null,
   login_time bigint default 0 not null
);
alter table LOGIN_EVENT alter column ID BIGINT default NEXT VALUE FOR "PUBLIC"."LOGIN_EVENT_ID_SEQ" auto_increment;

--观看事件表
create sequence observe_event_id_seq START WITH 40001;
create table observe_event (
   id bigint default observe_event_id_seq.nextval primary key,
   uid bigint not null,
   recordId bigint not null,
   in_Anchor boolean not null default false,
   temporary boolean default false not null,
   in_time bigint default 0 not null,
   out_time bigint default 0 not null
);
alter table OBSERVE_EVENT alter column ID BIGINT default NEXT VALUE FOR "PUBLIC"."OBSERVE_EVENT_ID_SEQ" auto_increment;