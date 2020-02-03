package org.seekloud.VideoMeeting.roomManager.http

import org.seekloud.VideoMeeting.roomManager.Boot._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import org.seekloud.VideoMeeting.protocol.ptcl.CommonRsp
import org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.http.RecordCommentProtocol
import org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.http.RecordCommentProtocol.CommentInfo
import org.seekloud.VideoMeeting.roomManager.models.dao.{RecordCommentDAO, RecordDao, UserInfoDao}
import org.seekloud.VideoMeeting.roomManager.models.SlickTables._
/**
  * created by benyafang on 2019/9/23 13:12
  * */

trait RecordCommentService extends ServiceUtils{
  import io.circe._
  import io.circe.syntax._
  import io.circe.generic.auto._

  private def addRecordCommentErrorRsp(msg:String) = CommonRsp(100012,msg)
  private val addRecordComment = (path("addRecordComment") & post){
      entity(as[Either[Error,RecordCommentProtocol.AddRecordCommentReq]]){
        case Right(req) =>
          dealFutureResult{
            UserInfoDao.searchById(req.commentUid).map{
              case Some(v) =>
                if(v.`sealed`){
                  complete(addRecordCommentErrorRsp(s"该用户已经被封号，无法评论"))
                }else{
                  dealFutureResult{
                    RecordDao.searchRecord(req.roomId,req.recordTime).map{v=>
                      if(v.nonEmpty){
                        dealFutureResult{
                          RecordCommentDAO.addRecordComment(rRecordComment(req.roomId,req.recordTime,req.comment,req.commentTime,req.commentUid,req.authorUidOpt,-1l,req.relativeTime)).map{r =>
                            complete(CommonRsp())
                          }.recover{
                            case e:Exception =>
                              log.debug(s"增加记录评论失败，error=$e")
                              complete(addRecordCommentErrorRsp(s"增加记录评论失败，error=$e"))
                          }
                        }
                      }else{
                        log.debug(s"增加记录评论失败，录像不存在")
                        complete(addRecordCommentErrorRsp(s"增加记录评论失败，录像不存在"))
                      }

                    }
                  }

                }
              case None =>
                complete(addRecordCommentErrorRsp(s"无法找到该用户"))
            }
          }
        case Left(error) =>
          log.debug(s"增加记录评论失败，请求错误，error=$error")
          complete(addRecordCommentErrorRsp(s"增加记录评论失败，请求错误，error=$error"))
      }

  }

  private def getRecordCommentListErrorRsp(msg:String) = CommonRsp(1000034,msg)
  private val getRecordCommentList = (path("getRecordCommentList") & post){
//    authUser{ _ =>
      entity(as[Either[Error,RecordCommentProtocol.GetRecordCommentListReq]]){
        case Right(req) =>
          dealFutureResult{
            RecordCommentDAO.getRecordComment(req.roomId,req.recordTime).flatMap{ls =>
              val userInfoList = (ls.map{r => r.commentUid} ++ ls.map(_.authorUid).filter(_.nonEmpty).map(_.get)).toSet.toList
              UserInfoDao.getUserDes(userInfoList).map{userInfoLs =>
                val resLs = ls.map{r =>
                  val commentUserInfo = userInfoLs.find(_.userId == r.commentUid)
                  commentUserInfo match{
                    case Some(userInfo) =>
                      r.authorUid match{
                        case Some(uid) =>
                          userInfoLs.find(_.userId == uid) match{
                            case Some(authorUerInfo) =>
                              CommentInfo(r.commentId,r.roomId,r.recordTime,r.comment,r.commentTime,r.relativeTime,r.commentUid,userInfo.userName,userInfo.headImgUrl,r.authorUid,Some(authorUerInfo.userName),Some(authorUerInfo.headImgUrl))

                            case None =>
                              CommentInfo(r.commentId,r.roomId,r.recordTime,r.comment,r.commentTime,r.relativeTime,r.commentUid,userInfo.userName,userInfo.headImgUrl)

                          }
                        case None =>
                          CommentInfo(r.commentId,r.roomId,r.recordTime,r.comment,r.commentTime,r.relativeTime,r.commentUid,userInfo.userName,userInfo.headImgUrl,r.authorUid)

                      }
                    case None =>
                      r.authorUid match{
                        case Some(uid) =>
                          userInfoLs.find(_.userId == uid) match{
                            case Some(authorUerInfo) =>
                              CommentInfo(r.commentId,r.roomId,r.recordTime,r.comment,r.commentTime,r.relativeTime,r.commentUid,"","",r.authorUid,Some(authorUerInfo.userName),Some(authorUerInfo.headImgUrl))

                            case None =>
                              CommentInfo(r.commentId,r.roomId,r.recordTime,r.comment,r.commentTime,r.relativeTime,r.commentUid,"","",r.authorUid)

                          }
                        case None =>
                          CommentInfo(r.commentId,r.roomId,r.recordTime,r.comment,r.commentTime,r.relativeTime,r.commentUid,"","",r.authorUid)
                      }

                  }
                }
                complete(RecordCommentProtocol.GetRecordCommentListRsp(resLs.toList))
              }.recover{
                case e:Exception =>
                  log.debug(s"获取评论列表失败，recover error:$e")
                  complete(getRecordCommentListErrorRsp(s"获取评论列表失败，recover error:$e"))
              }
            }.recover{
              case e:Exception =>
                log.debug(s"获取评论列表失败，recover error:$e")
                complete(getRecordCommentListErrorRsp(s"获取评论列表失败，recover error:$e"))
            }
          }

        case Left(error) =>
          log.debug(s"增加记录评论失败，请求错误，error=$error")
          complete(getRecordCommentListErrorRsp(s"增加记录评论失败，请求错误，error=$error"))
      }
//    }
  }


  val recordComment = pathPrefix("recordComment"){
    addRecordComment ~ getRecordCommentList

  }
}
