package org.seekloud.VideoMeeting.roomManager.models
// AUTO-GENERATED Slick data model
/** Stand-alone Slick data model for immediate use */
object SlickTables extends {
  val profile = slick.jdbc.H2Profile
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
    *  @param id Database column ID SqlType(BIGINT), AutoInc, PrimaryKey
    *  @param uid Database column UID SqlType(BIGINT)
    *  @param loginTime Database column LOGIN_TIME SqlType(BIGINT), Default(0) */
  case class rLoginEvent(id: Long, uid: Long, loginTime: Long = 0L)
  /** GetResult implicit for fetching rLoginEvent objects using plain SQL queries */
  implicit def GetResultrLoginEvent(implicit e0: GR[Long]): GR[rLoginEvent] = GR{
    prs => import prs._
      rLoginEvent.tupled((<<[Long], <<[Long], <<[Long]))
  }
  /** Table description of table LOGIN_EVENT. Objects of this class serve as prototypes for rows in queries. */
  class tLoginEvent(_tableTag: Tag) extends profile.api.Table[rLoginEvent](_tableTag, "LOGIN_EVENT") {
    def * = (id, uid, loginTime) <> (rLoginEvent.tupled, rLoginEvent.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = ((Rep.Some(id), Rep.Some(uid), Rep.Some(loginTime))).shaped.<>({r=>import r._; _1.map(_=> rLoginEvent.tupled((_1.get, _2.get, _3.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column ID SqlType(BIGINT), AutoInc, PrimaryKey */
    val id: Rep[Long] = column[Long]("ID", O.AutoInc, O.PrimaryKey)
    /** Database column UID SqlType(BIGINT) */
    val uid: Rep[Long] = column[Long]("UID")
    /** Database column LOGIN_TIME SqlType(BIGINT), Default(0) */
    val loginTime: Rep[Long] = column[Long]("LOGIN_TIME", O.Default(0L))
  }
  /** Collection-like TableQuery object for table tLoginEvent */
  lazy val tLoginEvent = new TableQuery(tag => new tLoginEvent(tag))

  /** Entity class storing rows of table tObserveEvent
    *  @param id Database column ID SqlType(BIGINT), AutoInc, PrimaryKey
    *  @param uid Database column UID SqlType(BIGINT)
    *  @param recordid Database column RECORDID SqlType(BIGINT)
    *  @param inAnchor Database column IN_ANCHOR SqlType(BOOLEAN), Default(false)
    *  @param temporary Database column TEMPORARY SqlType(BOOLEAN), Default(false)
    *  @param inTime Database column IN_TIME SqlType(BIGINT), Default(0)
    *  @param outTime Database column OUT_TIME SqlType(BIGINT), Default(0) */
  case class rObserveEvent(id: Long, uid: Long, recordid: Long, inAnchor: Boolean = false, temporary: Boolean = false, inTime: Long = 0L, outTime: Long = 0L)
  /** GetResult implicit for fetching rObserveEvent objects using plain SQL queries */
  implicit def GetResultrObserveEvent(implicit e0: GR[Long], e1: GR[Boolean]): GR[rObserveEvent] = GR{
    prs => import prs._
      rObserveEvent.tupled((<<[Long], <<[Long], <<[Long], <<[Boolean], <<[Boolean], <<[Long], <<[Long]))
  }
  /** Table description of table OBSERVE_EVENT. Objects of this class serve as prototypes for rows in queries. */
  class tObserveEvent(_tableTag: Tag) extends profile.api.Table[rObserveEvent](_tableTag, "OBSERVE_EVENT") {
    def * = (id, uid, recordid, inAnchor, temporary, inTime, outTime) <> (rObserveEvent.tupled, rObserveEvent.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = ((Rep.Some(id), Rep.Some(uid), Rep.Some(recordid), Rep.Some(inAnchor), Rep.Some(temporary), Rep.Some(inTime), Rep.Some(outTime))).shaped.<>({r=>import r._; _1.map(_=> rObserveEvent.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6.get, _7.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column ID SqlType(BIGINT), AutoInc, PrimaryKey */
    val id: Rep[Long] = column[Long]("ID", O.AutoInc, O.PrimaryKey)
    /** Database column UID SqlType(BIGINT) */
    val uid: Rep[Long] = column[Long]("UID")
    /** Database column RECORDID SqlType(BIGINT) */
    val recordid: Rep[Long] = column[Long]("RECORDID")
    /** Database column IN_ANCHOR SqlType(BOOLEAN), Default(false) */
    val inAnchor: Rep[Boolean] = column[Boolean]("IN_ANCHOR", O.Default(false))
    /** Database column TEMPORARY SqlType(BOOLEAN), Default(false) */
    val temporary: Rep[Boolean] = column[Boolean]("TEMPORARY", O.Default(false))
    /** Database column IN_TIME SqlType(BIGINT), Default(0) */
    val inTime: Rep[Long] = column[Long]("IN_TIME", O.Default(0L))
    /** Database column OUT_TIME SqlType(BIGINT), Default(0) */
    val outTime: Rep[Long] = column[Long]("OUT_TIME", O.Default(0L))
  }
  /** Collection-like TableQuery object for table tObserveEvent */
  lazy val tObserveEvent = new TableQuery(tag => new tObserveEvent(tag))

  /** Entity class storing rows of table tRecord
    *  @param id Database column ID SqlType(BIGINT), AutoInc, PrimaryKey
    *  @param roomId Database column ROOM_ID SqlType(BIGINT), Default(0)
    *  @param startTime Database column START_TIME SqlType(BIGINT), Default(0)
    *  @param coverImg Database column COVER_IMG SqlType(VARCHAR), Length(256,true), Default()
    *  @param recordName Database column RECORD_NAME SqlType(VARCHAR), Default()
    *  @param recordDes Database column RECORD_DES SqlType(VARCHAR), Default()
    *  @param viewNum Database column VIEW_NUM SqlType(INTEGER), Default(0)
    *  @param likeNum Database column LIKE_NUM SqlType(INTEGER), Default(0)
    *  @param duration Database column DURATION SqlType(VARCHAR), Length(100,true), Default()
    *  @param recordAddr Database column RECORD_ADDR SqlType(VARCHAR), Length(100,true), Default()
    *  @param attend Database column ATTEND SqlType(VARCHAR), Default() */
  case class rRecord(id: Long, roomId: Long = 0L, startTime: Long = 0L, coverImg: String = "", recordName: String = "", recordDes: String = "", viewNum: Int = 0, likeNum: Int = 0, duration: String = "", recordAddr: String = "", attend: String = "")
  /** GetResult implicit for fetching rRecord objects using plain SQL queries */
  implicit def GetResultrRecord(implicit e0: GR[Long], e1: GR[String], e2: GR[Int]): GR[rRecord] = GR{
    prs => import prs._
      rRecord.tupled((<<[Long], <<[Long], <<[Long], <<[String], <<[String], <<[String], <<[Int], <<[Int], <<[String], <<[String], <<[String]))
  }
  /** Table description of table RECORD. Objects of this class serve as prototypes for rows in queries. */
  class tRecord(_tableTag: Tag) extends profile.api.Table[rRecord](_tableTag, "RECORD") {
    def * = (id, roomId, startTime, coverImg, recordName, recordDes, viewNum, likeNum, duration, recordAddr, attend) <> (rRecord.tupled, rRecord.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = ((Rep.Some(id), Rep.Some(roomId), Rep.Some(startTime), Rep.Some(coverImg), Rep.Some(recordName), Rep.Some(recordDes), Rep.Some(viewNum), Rep.Some(likeNum), Rep.Some(duration), Rep.Some(recordAddr), Rep.Some(attend))).shaped.<>({r=>import r._; _1.map(_=> rRecord.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6.get, _7.get, _8.get, _9.get, _10.get, _11.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column ID SqlType(BIGINT), AutoInc, PrimaryKey */
    val id: Rep[Long] = column[Long]("ID", O.AutoInc, O.PrimaryKey)
    /** Database column ROOM_ID SqlType(BIGINT), Default(0) */
    val roomId: Rep[Long] = column[Long]("ROOM_ID", O.Default(0L))
    /** Database column START_TIME SqlType(BIGINT), Default(0) */
    val startTime: Rep[Long] = column[Long]("START_TIME", O.Default(0L))
    /** Database column COVER_IMG SqlType(VARCHAR), Length(256,true), Default() */
    val coverImg: Rep[String] = column[String]("COVER_IMG", O.Length(256,varying=true), O.Default(""))
    /** Database column RECORD_NAME SqlType(VARCHAR), Default() */
    val recordName: Rep[String] = column[String]("RECORD_NAME", O.Default(""))
    /** Database column RECORD_DES SqlType(VARCHAR), Default() */
    val recordDes: Rep[String] = column[String]("RECORD_DES", O.Default(""))
    /** Database column VIEW_NUM SqlType(INTEGER), Default(0) */
    val viewNum: Rep[Int] = column[Int]("VIEW_NUM", O.Default(0))
    /** Database column LIKE_NUM SqlType(INTEGER), Default(0) */
    val likeNum: Rep[Int] = column[Int]("LIKE_NUM", O.Default(0))
    /** Database column DURATION SqlType(VARCHAR), Length(100,true), Default() */
    val duration: Rep[String] = column[String]("DURATION", O.Length(100,varying=true), O.Default(""))
    /** Database column RECORD_ADDR SqlType(VARCHAR), Length(100,true), Default() */
    val recordAddr: Rep[String] = column[String]("RECORD_ADDR", O.Length(100,varying=true), O.Default(""))
    /** Database column ATTEND SqlType(VARCHAR), Default() */
    val attend: Rep[String] = column[String]("ATTEND", O.Default(""))
  }
  /** Collection-like TableQuery object for table tRecord */
  lazy val tRecord = new TableQuery(tag => new tRecord(tag))

  /** Entity class storing rows of table tRecordComment
    *  @param commentId Database column COMMENT_ID SqlType(BIGINT), AutoInc, PrimaryKey
    *  @param roomId Database column ROOM_ID SqlType(BIGINT)
    *  @param recordTime Database column RECORD_TIME SqlType(BIGINT)
    *  @param comment Database column COMMENT SqlType(VARCHAR), Default()
    *  @param commentTime Database column COMMENT_TIME SqlType(BIGINT)
    *  @param commentUid Database column COMMENT_UID SqlType(BIGINT)
    *  @param authorUid Database column AUTHOR_UID SqlType(BIGINT)
    *  @param relativeTime Database column RELATIVE_TIME SqlType(BIGINT), Default(0) */
  case class rRecordComment(commentId: Long, roomId: Long, recordTime: Long, comment: String = "", commentTime: Long, commentUid: Long, authorUid: Option[Long], relativeTime: Long = 0L)
  /** GetResult implicit for fetching rRecordComment objects using plain SQL queries */
  implicit def GetResultrRecordComment(implicit e0: GR[Long], e1: GR[String], e2: GR[Option[Long]]): GR[rRecordComment] = GR{
    prs => import prs._
      rRecordComment.tupled((<<[Long], <<[Long], <<[Long], <<[String], <<[Long], <<[Long], <<?[Long], <<[Long]))
  }
  /** Table description of table RECORD_COMMENT. Objects of this class serve as prototypes for rows in queries. */
  class tRecordComment(_tableTag: Tag) extends profile.api.Table[rRecordComment](_tableTag, "RECORD_COMMENT") {
    def * = (commentId, roomId, recordTime, comment, commentTime, commentUid, authorUid, relativeTime) <> (rRecordComment.tupled, rRecordComment.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = ((Rep.Some(commentId), Rep.Some(roomId), Rep.Some(recordTime), Rep.Some(comment), Rep.Some(commentTime), Rep.Some(commentUid), authorUid, Rep.Some(relativeTime))).shaped.<>({r=>import r._; _1.map(_=> rRecordComment.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6.get, _7, _8.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column COMMENT_ID SqlType(BIGINT), AutoInc, PrimaryKey */
    val commentId: Rep[Long] = column[Long]("COMMENT_ID", O.AutoInc, O.PrimaryKey)
    /** Database column ROOM_ID SqlType(BIGINT) */
    val roomId: Rep[Long] = column[Long]("ROOM_ID")
    /** Database column RECORD_TIME SqlType(BIGINT) */
    val recordTime: Rep[Long] = column[Long]("RECORD_TIME")
    /** Database column COMMENT SqlType(VARCHAR), Default() */
    val comment: Rep[String] = column[String]("COMMENT", O.Default(""))
    /** Database column COMMENT_TIME SqlType(BIGINT) */
    val commentTime: Rep[Long] = column[Long]("COMMENT_TIME")
    /** Database column COMMENT_UID SqlType(BIGINT) */
    val commentUid: Rep[Long] = column[Long]("COMMENT_UID")
    /** Database column AUTHOR_UID SqlType(BIGINT) */
    val authorUid: Rep[Option[Long]] = column[Option[Long]]("AUTHOR_UID")
    /** Database column RELATIVE_TIME SqlType(BIGINT), Default(0) */
    val relativeTime: Rep[Long] = column[Long]("RELATIVE_TIME", O.Default(0L))
  }
  /** Collection-like TableQuery object for table tRecordComment */
  lazy val tRecordComment = new TableQuery(tag => new tRecordComment(tag))

  /** Entity class storing rows of table tUserInfo
    *  @param uid Database column UID SqlType(BIGINT), AutoInc, PrimaryKey
    *  @param userName Database column USER_NAME SqlType(VARCHAR), Length(100,true)
    *  @param password Database column PASSWORD SqlType(VARCHAR), Length(100,true)
    *  @param roomid Database column ROOMID SqlType(BIGINT), AutoInc
    *  @param token Database column TOKEN SqlType(VARCHAR), Length(63,true), Default()
    *  @param tokenCreateTime Database column TOKEN_CREATE_TIME SqlType(BIGINT)
    *  @param headImg Database column HEAD_IMG SqlType(VARCHAR), Length(256,true), Default()
    *  @param email Database column EMAIL SqlType(VARCHAR), Length(256,true), Default()
    *  @param role Database column ROLE SqlType(BOOLEAN), Default(false)
    *  @param `sealed` Database column SEALED SqlType(BOOLEAN), Default(false)
    *  @param sealedUtilTime Database column SEALED_UTIL_TIME SqlType(BIGINT), Default(0) */
  case class rUserInfo(uid: Long, userName: String, password: String, roomid: Long, token: String = "", tokenCreateTime: Long, headImg: String = "", email: String = "", role: Boolean = false, `sealed`: Boolean = false, sealedUtilTime: Long = 0L)
  /** GetResult implicit for fetching rUserInfo objects using plain SQL queries */
  implicit def GetResultrUserInfo(implicit e0: GR[Long], e1: GR[String], e2: GR[Boolean]): GR[rUserInfo] = GR{
    prs => import prs._
      rUserInfo.tupled((<<[Long], <<[String], <<[String], <<[Long], <<[String], <<[Long], <<[String], <<[String], <<[Boolean], <<[Boolean], <<[Long]))
  }
  /** Table description of table USER_INFO. Objects of this class serve as prototypes for rows in queries.
    *  NOTE: The following names collided with Scala keywords and were escaped: sealed */
  class tUserInfo(_tableTag: Tag) extends profile.api.Table[rUserInfo](_tableTag, "USER_INFO") {
    def * = (uid, userName, password, roomid, token, tokenCreateTime, headImg, email, role, `sealed`, sealedUtilTime) <> (rUserInfo.tupled, rUserInfo.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = ((Rep.Some(uid), Rep.Some(userName), Rep.Some(password), Rep.Some(roomid), Rep.Some(token), Rep.Some(tokenCreateTime), Rep.Some(headImg), Rep.Some(email), Rep.Some(role), Rep.Some(`sealed`), Rep.Some(sealedUtilTime))).shaped.<>({r=>import r._; _1.map(_=> rUserInfo.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6.get, _7.get, _8.get, _9.get, _10.get, _11.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column UID SqlType(BIGINT), AutoInc, PrimaryKey */
    val uid: Rep[Long] = column[Long]("UID", O.AutoInc, O.PrimaryKey)
    /** Database column USER_NAME SqlType(VARCHAR), Length(100,true) */
    val userName: Rep[String] = column[String]("USER_NAME", O.Length(100,varying=true))
    /** Database column PASSWORD SqlType(VARCHAR), Length(100,true) */
    val password: Rep[String] = column[String]("PASSWORD", O.Length(100,varying=true))
    /** Database column ROOMID SqlType(BIGINT), AutoInc */
    val roomid: Rep[Long] = column[Long]("ROOMID", O.AutoInc)
    /** Database column TOKEN SqlType(VARCHAR), Length(63,true), Default() */
    val token: Rep[String] = column[String]("TOKEN", O.Length(63,varying=true), O.Default(""))
    /** Database column TOKEN_CREATE_TIME SqlType(BIGINT) */
    val tokenCreateTime: Rep[Long] = column[Long]("TOKEN_CREATE_TIME")
    /** Database column HEAD_IMG SqlType(VARCHAR), Length(256,true), Default() */
    val headImg: Rep[String] = column[String]("HEAD_IMG", O.Length(256,varying=true), O.Default(""))
    /** Database column EMAIL SqlType(VARCHAR), Length(256,true), Default() */
    val email: Rep[String] = column[String]("EMAIL", O.Length(256,varying=true), O.Default(""))
    /** Database column ROLE SqlType(BOOLEAN), Default(false) */
    val role: Rep[Boolean] = column[Boolean]("ROLE", O.Default(false))
    /** Database column SEALED SqlType(BOOLEAN), Default(false)
      *  NOTE: The name was escaped because it collided with a Scala keyword. */
    val `sealed`: Rep[Boolean] = column[Boolean]("SEALED", O.Default(false))
    /** Database column SEALED_UTIL_TIME SqlType(BIGINT), Default(0) */
    val sealedUtilTime: Rep[Long] = column[Long]("SEALED_UTIL_TIME", O.Default(0L))

    /** Uniqueness Index over (userName) (database name USER_INFO_USER_NAME_UINDEX) */
    val index1 = index("USER_INFO_USER_NAME_UINDEX", userName, unique=true)
  }
  /** Collection-like TableQuery object for table tUserInfo */
  lazy val tUserInfo = new TableQuery(tag => new tUserInfo(tag))
}
