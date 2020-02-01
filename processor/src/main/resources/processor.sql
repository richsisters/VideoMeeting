--processor数据表，创建时间：2019/7/18 用于postgreSQL数据库

--房间信息表
CREATE SEQUENCE mpd_info_id_seq START WITH 1000000001;
CREATE TABLE mpd_info (
  id                BIGINT PRIMARY KEY DEFAULT nextval('mpd_info_id_seq'),
  room_id           BIGINT         NOT NULL   DEFAULT  0,
  start_time        BIGINT         NOT NULL   DEFAULT  0,
  end_time          BIGINT         NOT NULL   DEFAULT  0,
  mpd_addr          VARCHAR(128)   NOT NULL   DEFAULT ''
);
ALTER SEQUENCE mpd_info_id_seq OWNED BY mpd_info.id;

