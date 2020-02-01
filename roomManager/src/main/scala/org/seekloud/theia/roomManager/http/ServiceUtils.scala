package org.seekloud.VideoMeeting.roomManager.http

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse}
import akka.http.scaladsl.server
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import io.circe.{Decoder, Error}
import io.circe.parser.decode
import org.seekloud.VideoMeeting.protocol.ptcl._
import org.seekloud.VideoMeeting.roomManager.common.AppSettings
import org.seekloud.VideoMeeting.roomManager.utils.{CirceSupport, SecureUtil}

import org.seekloud.VideoMeeting.roomManager.utils.SecureUtil
import org.seekloud.VideoMeeting.roomManager.utils.SecureUtil.PostEnvelope
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
  * User: Taoz
  * Date: 11/18/2016
  * Time: 7:57 PM
  */

object ServiceUtils{
  private val log = LoggerFactory.getLogger(this.getClass)

  case class CommonRsp(errCode: Int = 0, msg: String = "ok")

  final val SignatureError = CommonRsp(1000001, "signature error.")

  final val RequestTimeout = CommonRsp(1000003, "request timestamp is too old.")

  final val AppClientIdError = CommonRsp(1000002, "appClientId error.")

  final val INTERNAL_ERROR = CommonRsp(10001, "Internal error.")

  final val JsonParseError = CommonRsp(10002, "Json parse error.")
}

trait ServiceUtils extends CirceSupport with SessionBase{

  import ServiceUtils._
  import io.circe.generic.auto._


  def htmlResponse(html: String): HttpResponse = {
    HttpResponse(entity = HttpEntity(ContentTypes.`text/html(UTF-8)`, html))
  }

  def jsonResponse(json: String): HttpResponse = {
    HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, json))
  }

  def dealFutureResult(future: => Future[server.Route]): Route = {
    onComplete(future) {
      case Success(rst) => rst
      case Failure(e) =>
        e.printStackTrace()
        log.error("internal error: {}", e.getMessage)
        complete(CommonRsp(1000, "internal error."))
    }
  }

  def ensureAuth(
                  appClientId: String,
                  timestamp: String,
                  nonce: String,
                  sn: String,
                  data: List[String],
                  signature: String
                )(f: => Future[server.Route]): server.Route = {
    val p = getSecureKey(appClientId) match {
      case Some(secureKey) =>
        val paramList = List(appClientId.toString, timestamp, nonce, sn) ::: data
        if (timestamp.toLong + 120000 < System.currentTimeMillis()) {
          Future.successful(complete(RequestTimeout))
        } else if (SecureUtil.checkSignature(paramList, signature, secureKey)) {
          f
        } else {
          Future.successful(complete(SignatureError))
        }
      case None =>
        Future.successful(complete(AppClientIdError))
    }
    dealFutureResult(p)
  }

  def ensurePostEnvelope(e: PostEnvelope)(f: => Future[server.Route]) = {
    ensureAuth(e.appId, e.timestamp, e.nonce, e.sn, List(e.data), e.signature)(f)
  }

  private def getSecureKey(appId: String) = AppSettings.appSecureMap.get(appId)

  def dealPostReq[A](f: A => Future[server.Route])(implicit decoder: Decoder[A]): server.Route = {
    entity(as[Either[Error, PostEnvelope]]) {
      case Right(envelope) =>
        ensurePostEnvelope(envelope) {
          decode[A](envelope.data) match {
            case Right(req) =>
              f(req)

            case Left(e) =>
              log.error(s"json parse detail type error: $e")
              Future.successful(complete(JsonParseError))
          }
        }

      case Left(e) =>
        log.error(s"json parse PostEnvelope error: $e")
        complete(JsonParseError)
    }
  }

  def dealGetReq(f: => Future[server.Route]): server.Route = {
    entity(as[Either[Error, PostEnvelope]]) {
      case Right(envelope) =>
        ensurePostEnvelope(envelope) {
          f
        }

      case Left(e) =>
        log.error(s"json parse PostEnvelope error: $e")
        complete(JsonParseError)
    }
  }

}
