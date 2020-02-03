-- roomManager 数据表 创建时间： 2020/02/03


-- 用户信息表
create sequence user_info_uid_seq start with 10001;
create sequence user_info_rid_seq start with 20001;
create table user_info (
  uid bigint primary key not null default user_info_uid_seq.nextval,
  user_name varchar(100) not null,
  password varchar(100) not null,
  roomId bigint not null default user_info_rid_seq.nextval,
  token varchar(63) not null default '',
  token_create_time bigint not null,
  head_img varchar(256) not null default '',
  email varchar(256) not null default '',
  role boolean not null default false -- 用户角色，1为主持人，0为参会者
);

create sequence group_seq;
create table test3(id bigint default group_seq.nextval primary key);