package org.seekloud.VideoMeeting.processor.models

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
  lazy val schema: profile.SchemaDescription = tMpdInfo.schema
  @deprecated("Use .schema instead of .ddl", "3.0")
  def ddl = schema

  /** Entity class storing rows of table tMpdInfo
    *  @param id Database column id SqlType(bigserial), AutoInc, PrimaryKey
    *  @param roomId Database column room_id SqlType(int8), Default(0)
    *  @param startTime Database column start_time SqlType(int8), Default(0)
    *  @param endTime Database column end_time SqlType(int8), Default(0)
    *  @param mpdAddr Database column mpd_addr SqlType(varchar), Length(128,true), Default() */
  case class rMpdInfo(id: Long, roomId: Long = 0L, startTime: Long = 0L, endTime: Long = 0L, mpdAddr: String = "")
  /** GetResult implicit for fetching rMpdInfo objects using plain SQL queries */
  implicit def GetResultrMpdInfo(implicit e0: GR[Long], e1: GR[String]): GR[rMpdInfo] = GR{
    prs => import prs._
      rMpdInfo.tupled((<<[Long], <<[Long], <<[Long], <<[Long], <<[String]))
  }
  /** Table description of table mpd_info. Objects of this class serve as prototypes for rows in queries. */
  class tMpdInfo(_tableTag: Tag) extends profile.api.Table[rMpdInfo](_tableTag, "mpd_info") {
    def * = (id, roomId, startTime, endTime, mpdAddr) <> (rMpdInfo.tupled, rMpdInfo.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = ((Rep.Some(id), Rep.Some(roomId), Rep.Some(startTime), Rep.Some(endTime), Rep.Some(mpdAddr))).shaped.<>({r=>import r._; _1.map(_=> rMpdInfo.tupled((_1.get, _2.get, _3.get, _4.get, _5.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column id SqlType(bigserial), AutoInc, PrimaryKey */
    val id: Rep[Long] = column[Long]("id", O.AutoInc, O.PrimaryKey)
    /** Database column room_id SqlType(int8), Default(0) */
    val roomId: Rep[Long] = column[Long]("room_id", O.Default(0L))
    /** Database column start_time SqlType(int8), Default(0) */
    val startTime: Rep[Long] = column[Long]("start_time", O.Default(0L))
    /** Database column end_time SqlType(int8), Default(0) */
    val endTime: Rep[Long] = column[Long]("end_time", O.Default(0L))
    /** Database column mpd_addr SqlType(varchar), Length(128,true), Default() */
    val mpdAddr: Rep[String] = column[String]("mpd_addr", O.Length(128,varying=true), O.Default(""))
  }
  /** Collection-like TableQuery object for table tMpdInfo */
  lazy val tMpdInfo = new TableQuery(tag => new tMpdInfo(tag))
}
