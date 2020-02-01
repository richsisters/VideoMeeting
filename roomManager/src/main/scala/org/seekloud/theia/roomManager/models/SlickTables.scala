package org.seekloud.VideoMeeting.roomManager.models
// AUTO-GENERATED Slick data model
/** Stand-alone Slick data model for immediate use */
object SlickTables extends {
  val profile = slick.jdbc.PostgresProfile
} with SlickTables

/** Slick data model trait for extension, choice of backend or usage in the cake pattern. (Make sure to initialize this late.) */
trait SlickTables {
  val profile: slick.jdbc.JdbcProfile
  import profile.api._
  import slick.model.ForeignKeyAction
  // NOTE: GetResult mappers for plain SQL are only generated for tables where Slick knows how to map the types of all columns.
  import slick.jdbc.{GetResult => GR}

  /** DDL for all tables. Call .create to execute. */
  lazy val schema: profile.SchemaDescription = tLoginEvent.schema ++ tObserveEvent.schema ++ tRecord.schema ++ tRecordComment.schema ++ tUserInfo.schema
  @deprecated("Use .schema instead of .ddl", "3.0")
  def ddl = schema

  /** Entity class storing rows of table tLoginEvent
    *  @param id Database column id SqlType(bigserial), AutoInc, PrimaryKey
    *  @param uid Database column uid SqlType(int8)
    *  @param loginTime Database column login_time SqlType(int8), Default(0) */
  case class rLoginEvent(id: Long, uid: Long, loginTime: Long = 0L)
  /** GetResult implicit for fetching rLoginEvent objects using plain SQL queries */
  implicit def GetResultrLoginEvent(implicit e0: GR[Long]): GR[rLoginEvent] = GR{
    prs => import prs._
      rLoginEvent.tupled((<<[Long], <<[Long], <<[Long]))
  }
  /** Table description of table login_event. Objects of this class serve as prototypes for rows in queries. */
  class tLoginEvent(_tableTag: Tag) extends profile.api.Table[rLoginEvent](_tableTag, "login_event") {
    def * = (id, uid, loginTime) <> (rLoginEvent.tupled, rLoginEvent.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = ((Rep.Some(id), Rep.Some(uid), Rep.Some(loginTime))).shaped.<>({r=>import r._; _1.map(_=> rLoginEvent.tupled((_1.get, _2.get, _3.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column id SqlType(bigserial), AutoInc, PrimaryKey */
    val id: Rep[Long] = column[Long]("id", O.AutoInc, O.PrimaryKey)
    /** Database column uid SqlType(int8) */
    val uid: Rep[Long] = column[Long]("uid")
    /** Database column login_time SqlType(int8), Default(0) */
    val loginTime: Rep[Long] = column[Long]("login_time", O.Default(0L))
  }
  /** Collection-like TableQuery object for table tLoginEvent */
  lazy val tLoginEvent = new TableQuery(tag => new tLoginEvent(tag))

  /** Entity class storing rows of table tObserveEvent
    *  @param id Database column id SqlType(bigserial), AutoInc, PrimaryKey
    *  @param uid Database column uid SqlType(int8)
    *  @param recordid Database column recordid SqlType(int8)
    *  @param inAnchor Database column in_anchor SqlType(bool), Default(false)
    *  @param temporary Database column temporary SqlType(bool), Default(false)
    *  @param inTime Database column in_time SqlType(int8), Default(0)
    *  @param outTime Database column out_time SqlType(int8), Default(0) */
  case class rObserveEvent(id: Long, uid: Long, recordid: Long, inAnchor: Boolean = false, temporary: Boolean = false, inTime: Long = 0L, outTime: Long = 0L)
  /** GetResult implicit for fetching rObserveEvent objects using plain SQL queries */
  implicit def GetResultrObserveEvent(implicit e0: GR[Long], e1: GR[Boolean]): GR[rObserveEvent] = GR{
    prs => import prs._
      rObserveEvent.tupled((<<[Long], <<[Long], <<[Long], <<[Boolean], <<[Boolean], <<[Long], <<[Long]))
  }
  /** Table description of table observe_event. Objects of this class serve as prototypes for rows in queries. */
  class tObserveEvent(_tableTag: Tag) extends profile.api.Table[rObserveEvent](_tableTag, "observe_event") {
    def * = (id, uid, recordid, inAnchor, temporary, inTime, outTime) <> (rObserveEvent.tupled, rObserveEvent.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = ((Rep.Some(id), Rep.Some(uid), Rep.Some(recordid), Rep.Some(inAnchor), Rep.Some(temporary), Rep.Some(inTime), Rep.Some(outTime))).shaped.<>({r=>import r._; _1.map(_=> rObserveEvent.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6.get, _7.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column id SqlType(bigserial), AutoInc, PrimaryKey */
    val id: Rep[Long] = column[Long]("id", O.AutoInc, O.PrimaryKey)
    /** Database column uid SqlType(int8) */
    val uid: Rep[Long] = column[Long]("uid")
    /** Database column recordid SqlType(int8) */
    val recordid: Rep[Long] = column[Long]("recordid")
    /** Database column in_anchor SqlType(bool), Default(false) */
    val inAnchor: Rep[Boolean] = column[Boolean]("in_anchor", O.Default(false))
    /** Database column temporary SqlType(bool), Default(false) */
    val temporary: Rep[Boolean] = column[Boolean]("temporary", O.Default(false))
    /** Database column in_time SqlType(int8), Default(0) */
    val inTime: Rep[Long] = column[Long]("in_time", O.Default(0L))
    /** Database column out_time SqlType(int8), Default(0) */
    val outTime: Rep[Long] = column[Long]("out_time", O.Default(0L))
  }
  /** Collection-like TableQuery object for table tObserveEvent */
  lazy val tObserveEvent = new TableQuery(tag => new tObserveEvent(tag))

  /** Entity class storing rows of table tRecord
    *  @param id Database column id SqlType(bigserial), AutoInc, PrimaryKey
    *  @param roomid Database column roomid SqlType(int8), Default(0)
    *  @param startTime Database column start_time SqlType(int8), Default(0)
    *  @param coverImg Database column cover_img SqlType(varchar), Length(256,true), Default()
    *  @param recordName Database column record_name SqlType(varchar), Default()
    *  @param recordDes Database column record_des SqlType(varchar), Default()
    *  @param viewNum Database column view_num SqlType(int4), Default(0)
    *  @param likeNum Database column like_num SqlType(int4), Default(0)
    *  @param duration Database column duration SqlType(varchar), Length(100,true), Default() */
  case class rRecord(id: Long, roomid: Long = 0L, startTime: Long = 0L, coverImg: String = "", recordName: String = "", recordDes: String = "", viewNum: Int = 0, likeNum: Int = 0, duration: String = "")
  /** GetResult implicit for fetching rRecord objects using plain SQL queries */
  implicit def GetResultrRecord(implicit e0: GR[Long], e1: GR[String], e2: GR[Int]): GR[rRecord] = GR{
    prs => import prs._
      rRecord.tupled((<<[Long], <<[Long], <<[Long], <<[String], <<[String], <<[String], <<[Int], <<[Int], <<[String]))
  }
  /** Table description of table record. Objects of this class serve as prototypes for rows in queries. */
  class tRecord(_tableTag: Tag) extends profile.api.Table[rRecord](_tableTag, "record") {
    def * = (id, roomid, startTime, coverImg, recordName, recordDes, viewNum, likeNum, duration) <> (rRecord.tupled, rRecord.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = ((Rep.Some(id), Rep.Some(roomid), Rep.Some(startTime), Rep.Some(coverImg), Rep.Some(recordName), Rep.Some(recordDes), Rep.Some(viewNum), Rep.Some(likeNum), Rep.Some(duration))).shaped.<>({r=>import r._; _1.map(_=> rRecord.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6.get, _7.get, _8.get, _9.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column id SqlType(bigserial), AutoInc, PrimaryKey */
    val id: Rep[Long] = column[Long]("id", O.AutoInc, O.PrimaryKey)
    /** Database column roomid SqlType(int8), Default(0) */
    val roomid: Rep[Long] = column[Long]("roomid", O.Default(0L))
    /** Database column start_time SqlType(int8), Default(0) */
    val startTime: Rep[Long] = column[Long]("start_time", O.Default(0L))
    /** Database column cover_img SqlType(varchar), Length(256,true), Default() */
    val coverImg: Rep[String] = column[String]("cover_img", O.Length(256,varying=true), O.Default(""))
    /** Database column record_name SqlType(varchar), Default() */
    val recordName: Rep[String] = column[String]("record_name", O.Default(""))
    /** Database column record_des SqlType(varchar), Default() */
    val recordDes: Rep[String] = column[String]("record_des", O.Default(""))
    /** Database column view_num SqlType(int4), Default(0) */
    val viewNum: Rep[Int] = column[Int]("view_num", O.Default(0))
    /** Database column like_num SqlType(int4), Default(0) */
    val likeNum: Rep[Int] = column[Int]("like_num", O.Default(0))
    /** Database column duration SqlType(varchar), Length(100,true), Default() */
    val duration: Rep[String] = column[String]("duration", O.Length(100,varying=true), O.Default(""))
  }
  /** Collection-like TableQuery object for table tRecord */
  lazy val tRecord = new TableQuery(tag => new tRecord(tag))

  /** Entity class storing rows of table tRecordComment
    *  @param roomId Database column room_id SqlType(int8)
    *  @param recordTime Database column record_time SqlType(int8)
    *  @param comment Database column comment SqlType(varchar), Default()
    *  @param commentTime Database column comment_time SqlType(int8)
    *  @param commentUid Database column comment_uid SqlType(int8)
    *  @param authorUid Database column author_uid SqlType(int8), Default(None)
    *  @param commentId Database column comment_id SqlType(bigserial), AutoInc, PrimaryKey
    *  @param relativeTime Database column relative_time SqlType(int8), Default(0) */
  case class rRecordComment(roomId: Long, recordTime: Long, comment: String = "", commentTime: Long, commentUid: Long, authorUid: Option[Long] = None, commentId: Long, relativeTime: Long = 0L)
  /** GetResult implicit for fetching rRecordComment objects using plain SQL queries */
  implicit def GetResultrRecordComment(implicit e0: GR[Long], e1: GR[String], e2: GR[Option[Long]]): GR[rRecordComment] = GR{
    prs => import prs._
      rRecordComment.tupled((<<[Long], <<[Long], <<[String], <<[Long], <<[Long], <<?[Long], <<[Long], <<[Long]))
  }
  /** Table description of table record_comment. Objects of this class serve as prototypes for rows in queries. */
  class tRecordComment(_tableTag: Tag) extends profile.api.Table[rRecordComment](_tableTag, "record_comment") {
    def * = (roomId, recordTime, comment, commentTime, commentUid, authorUid, commentId, relativeTime) <> (rRecordComment.tupled, rRecordComment.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = ((Rep.Some(roomId), Rep.Some(recordTime), Rep.Some(comment), Rep.Some(commentTime), Rep.Some(commentUid), authorUid, Rep.Some(commentId), Rep.Some(relativeTime))).shaped.<>({r=>import r._; _1.map(_=> rRecordComment.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6, _7.get, _8.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column room_id SqlType(int8) */
    val roomId: Rep[Long] = column[Long]("room_id")
    /** Database column record_time SqlType(int8) */
    val recordTime: Rep[Long] = column[Long]("record_time")
    /** Database column comment SqlType(varchar), Default() */
    val comment: Rep[String] = column[String]("comment", O.Default(""))
    /** Database column comment_time SqlType(int8) */
    val commentTime: Rep[Long] = column[Long]("comment_time")
    /** Database column comment_uid SqlType(int8) */
    val commentUid: Rep[Long] = column[Long]("comment_uid")
    /** Database column author_uid SqlType(int8), Default(None) */
    val authorUid: Rep[Option[Long]] = column[Option[Long]]("author_uid", O.Default(None))
    /** Database column comment_id SqlType(bigserial), AutoInc, PrimaryKey */
    val commentId: Rep[Long] = column[Long]("comment_id", O.AutoInc, O.PrimaryKey)
    /** Database column relative_time SqlType(int8), Default(0) */
    val relativeTime: Rep[Long] = column[Long]("relative_time", O.Default(0L))
  }
  /** Collection-like TableQuery object for table tRecordComment */
  lazy val tRecordComment = new TableQuery(tag => new tRecordComment(tag))

  /** Entity class storing rows of table tUserInfo
    *  @param uid Database column uid SqlType(bigserial), AutoInc, PrimaryKey
    *  @param userName Database column user_name SqlType(varchar), Length(100,true)
    *  @param password Database column password SqlType(varchar), Length(100,true)
    *  @param roomid Database column roomid SqlType(bigserial), AutoInc
    *  @param token Database column token SqlType(varchar), Length(63,true), Default()
    *  @param tokenCreateTime Database column token_create_time SqlType(int8), Default(2592000)
    *  @param headImg Database column head_img SqlType(varchar), Length(256,true), Default()
    *  @param coverImg Database column cover_img SqlType(varchar), Length(256,true), Default()
    *  @param email Database column email SqlType(varchar), Length(100,true), Default()
    *  @param createTime Database column create_time SqlType(int8), Default(0)
    *  @param rtmpToken Database column rtmp_token SqlType(varchar), Length(256,true), Default()
    *  @param `sealed` Database column sealed SqlType(bool), Default(false)
    *  @param sealedUtilTime Database column sealed_util_time SqlType(int8), Default(-1)
    *  @param allowAnchor Database column allow_anchor SqlType(bool), Default(true) */
  case class rUserInfo(uid: Long, userName: String, password: String, roomid: Long, token: String = "", tokenCreateTime: Long = 2592000L, headImg: String = "", coverImg: String = "", email: String = "", createTime: Long = 0L, rtmpToken: String = "", `sealed`: Boolean = false, sealedUtilTime: Long = -1L, allowAnchor: Boolean = true)
  /** GetResult implicit for fetching rUserInfo objects using plain SQL queries */
  implicit def GetResultrUserInfo(implicit e0: GR[Long], e1: GR[String], e2: GR[Boolean]): GR[rUserInfo] = GR{
    prs => import prs._
      rUserInfo.tupled((<<[Long], <<[String], <<[String], <<[Long], <<[String], <<[Long], <<[String], <<[String], <<[String], <<[Long], <<[String], <<[Boolean], <<[Long], <<[Boolean]))
  }
  /** Table description of table user_info. Objects of this class serve as prototypes for rows in queries.
    *  NOTE: The following names collided with Scala keywords and were escaped: sealed */
  class tUserInfo(_tableTag: Tag) extends profile.api.Table[rUserInfo](_tableTag, "user_info") {
    def * = (uid, userName, password, roomid, token, tokenCreateTime, headImg, coverImg, email, createTime, rtmpToken, `sealed`, sealedUtilTime, allowAnchor) <> (rUserInfo.tupled, rUserInfo.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = ((Rep.Some(uid), Rep.Some(userName), Rep.Some(password), Rep.Some(roomid), Rep.Some(token), Rep.Some(tokenCreateTime), Rep.Some(headImg), Rep.Some(coverImg), Rep.Some(email), Rep.Some(createTime), Rep.Some(rtmpToken), Rep.Some(`sealed`), Rep.Some(sealedUtilTime), Rep.Some(allowAnchor))).shaped.<>({r=>import r._; _1.map(_=> rUserInfo.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6.get, _7.get, _8.get, _9.get, _10.get, _11.get, _12.get, _13.get, _14.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column uid SqlType(bigserial), AutoInc, PrimaryKey */
    val uid: Rep[Long] = column[Long]("uid", O.AutoInc, O.PrimaryKey)
    /** Database column user_name SqlType(varchar), Length(100,true) */
    val userName: Rep[String] = column[String]("user_name", O.Length(100,varying=true))
    /** Database column password SqlType(varchar), Length(100,true) */
    val password: Rep[String] = column[String]("password", O.Length(100,varying=true))
    /** Database column roomid SqlType(bigserial), AutoInc */
    val roomid: Rep[Long] = column[Long]("roomid", O.AutoInc)
    /** Database column token SqlType(varchar), Length(63,true), Default() */
    val token: Rep[String] = column[String]("token", O.Length(63,varying=true), O.Default(""))
    /** Database column token_create_time SqlType(int8), Default(2592000) */
    val tokenCreateTime: Rep[Long] = column[Long]("token_create_time", O.Default(2592000L))
    /** Database column head_img SqlType(varchar), Length(256,true), Default() */
    val headImg: Rep[String] = column[String]("head_img", O.Length(256,varying=true), O.Default(""))
    /** Database column cover_img SqlType(varchar), Length(256,true), Default() */
    val coverImg: Rep[String] = column[String]("cover_img", O.Length(256,varying=true), O.Default(""))
    /** Database column email SqlType(varchar), Length(100,true), Default() */
    val email: Rep[String] = column[String]("email", O.Length(100,varying=true), O.Default(""))
    /** Database column create_time SqlType(int8), Default(0) */
    val createTime: Rep[Long] = column[Long]("create_time", O.Default(0L))
    /** Database column rtmp_token SqlType(varchar), Length(256,true), Default() */
    val rtmpToken: Rep[String] = column[String]("rtmp_token", O.Length(256,varying=true), O.Default(""))
    /** Database column sealed SqlType(bool), Default(false)
      *  NOTE: The name was escaped because it collided with a Scala keyword. */
    val `sealed`: Rep[Boolean] = column[Boolean]("sealed", O.Default(false))
    /** Database column sealed_util_time SqlType(int8), Default(-1) */
    val sealedUtilTime: Rep[Long] = column[Long]("sealed_util_time", O.Default(-1L))
    /** Database column allow_anchor SqlType(bool), Default(true) */
    val allowAnchor: Rep[Boolean] = column[Boolean]("allow_anchor", O.Default(true))
  }
  /** Collection-like TableQuery object for table tUserInfo */
  lazy val tUserInfo = new TableQuery(tag => new tUserInfo(tag))
}
