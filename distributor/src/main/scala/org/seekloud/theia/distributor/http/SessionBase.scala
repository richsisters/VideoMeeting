/*
 * Copyright 2018 seekloud (https://github.com/seekloud)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.seekloud.VideoMeeting.distributor.http

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server
import akka.http.scaladsl.server.Directives.{complete, onComplete, redirect, reject}
import akka.http.scaladsl.server.{Directive0, Directive1, ValidationRejection}
import akka.http.scaladsl.server.directives.BasicDirectives
import org.seekloud.VideoMeeting.distributor.common.AppSettings
import org.seekloud.VideoMeeting.distributor.utils.{CirceSupport, SessionSupport}
import com.sun.xml.internal.ws.encoding.soap.DeserializationException
import org.slf4j.LoggerFactory


/**
  * User: Taoz
  * Date: 12/4/2016
  * Time: 7:57 PM
  */

object SessionBase {
  private val logger = LoggerFactory.getLogger(this.getClass)

  private val sessionTimeout = 24 * 60 * 60 * 1000
  val SessionTypeKey = "STKey"


  object VideoUserSessionKey {
    val SESSION_TYPE = "videoSession"
    val userId = "userId"
    val username = "username"
    val pwd = "pwd"
    val loginTime = "video_loginTime"
    //添加是否是主播
  }

  case class VideoUserInfo (
                           username: String,
                           userId: Long,
                           pwd: String
                           )
  case class VideoSession(
                         videoUserInfo: VideoUserInfo,
                         ){
    def toVideoSessionMap = {
      Map(
        SessionTypeKey -> VideoUserSessionKey.SESSION_TYPE,
        VideoUserSessionKey.username -> videoUserInfo.username,
        VideoUserSessionKey.userId -> videoUserInfo.userId.toString,
        VideoUserSessionKey.pwd -> videoUserInfo.pwd
      )
    }
  }


  implicit class SessionTransformer(sessionMap: Map[String, String]) {

    def toVideoSession: Option[VideoSession] = {
      logger.debug(s"toVideoSession: change map to session, ${sessionMap.mkString(",")}")
      try{
        if(sessionMap.get(SessionTypeKey).exists(_.equals(VideoUserSessionKey.SESSION_TYPE))){
          Some(VideoSession(
            VideoUserInfo(sessionMap(VideoUserSessionKey.username),
              sessionMap(VideoUserSessionKey.userId).toLong,
              sessionMap(VideoUserSessionKey.pwd)
            )
          ))
        }else{
          logger.debug("no session type in the session")
          None
        }
      } catch {
        case e: Exception =>
          e.printStackTrace()
          logger.warn(s"toVideoSession: ${e.getMessage}")
          None
      }
    }
  }

}

trait SessionBase extends SessionSupport {

  import SessionBase._
  import io.circe.generic.auto._
  
  override val sessionEncoder = SessionSupport.PlaySessionEncoder
  override val sessionConfig = AppSettings.sessionConfig

//  def noSessionError(message:String = "no session") = ErrorRsp(1000102,s"$message")


  protected def setVideoSession(videoSession: VideoSession): Directive0 = setSession(videoSession.toVideoSessionMap)

  def videoAuth(f: VideoSession => server.Route) = optionalVideoSession {
    case Some(session) =>
      f(session)
    case None =>
      redirect("/", StatusCodes.SeeOther)
  }


  protected val optionalVideoSession: Directive1[Option[VideoSession]] = optionalSession.flatMap{
    case Right(sessionMap) => BasicDirectives.provide(sessionMap.toVideoSession)
    case Left(error) =>
      logger.debug(error)
      BasicDirectives.provide(None)
  }

}
