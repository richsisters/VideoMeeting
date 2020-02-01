-- roomManager 数据表 创建时间： 2019/7/15

create sequence user_info_uid_seq1 start with 100201;
create sequence user_info_rid_seq1 start with 1000201;
create sequence login_event_id_seq
  START WITH 2000001
  INCREMENT BY 1;
create sequence observe_event_id_seq
START WITH 3000001
INCREMENT BY 1;

create table user_info (
  uid               bigint primary key default nextval('user_info_uid_seq1'),
  user_name         varchar(100)  not null,
  password          varchar(100)  not null,
  roomId            bigint        not null default nextval('user_info_rid_seq1'),
  token             varchar(63)   not null default '',
  token_create_time bigint        not null,
  head_img           varchar(256) not null default '',
  cover_img         varchar(256) not null default '',
  email             varchar(256) not null default '',
  create_time       bigint        not null,
  rtmp_token        varchar(256) not null default '',
  sealed            BOOLEAN NOT NULL DEFAULT FALSE,
  sealed_util_time  BIGINT NOT NULL DEFAULT 0,
  allow_anchor      BOOLEAN NOT NULL DEFAULT TRUE
);

alter sequence user_info_uid_seq owned by user_info.uid;
alter sequence user_info_rid_seq owned by user_info.roomId;
create unique index user_info_user_name_index on user_info(user_name);

ALTER TABLE record ADD cover_img VARCHAR(256) NOT NULL DEFAULT '';
ALTER TABLE record ADD record_name VARCHAR NOT NULL DEFAULT '';
ALTER TABLE record ADD record_des VARCHAR NOT NULL DEFAULT '';
ALTER TABLE record ADD view_num INTEGER NOT NULL DEFAULT 0;
ALTER TABLE record ADD like_num INTEGER NOT NULL DEFAULT 0;

CREATE TABLE record_comment(
  room_id       bigint  NOT NULL ,
  record_time   bigint  NOT NULL ,
  comment       VARCHAR NOT NULL DEFAULT '',
  comment_time  bigint  NOT NULL ,
  comment_uid   bigint  NOT NULL ,
  author_uid    bigint,--被评论的用户id,如果是None，就是回复主播
);

ALTER TABLE record ADD duration VARCHAR (100) NOT NULL DEFAULT '';
ALTER TABLE public.record_comment ADD comment_id BIGSERIAL NOT NULL;
ALTER TABLE public.record_comment ADD CONSTRAINT record_comment_comment_id_pk PRIMARY KEY (comment_id);

ALTER TABLE record_comment ADD relative_time BIGINT NOT NULL DEFAULT 0;



--登录事件表
create table login_event (
  id    bigint primary key  default nextval('login_event_id_seq'),
  uid               bigint not null,
  login_time        bigint default 0 not null
);

--观看事件表
create table observe_event (
  id    bigint primary key  default nextval('observe_event_id_seq'),
  uid               bigint not null,
  recordId          bigint not null,
  in_Anchor         boolean not null default false, 
  temporary         boolean default false not null,
  in_time        bigint default 0 not null,
  out_time       bigint default 0 not null
);

ALTER TABLE record ADD COLUMN record_addr VARCHAR(100) NOT NULL DEFAULT '';