package org.seekloud.VideoMeeting.rtpServer.http

import akka.http.scaladsl.server.Directives.{complete, path, pathPrefix}
import org.seekloud.VideoMeeting.rtpServer.utils.CirceSupport
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import org.seekloud.VideoMeeting.rtpServer.Boot.{dataStoreActor, publishManager, queryInfoManager, streamManager, userManager}
import org.seekloud.VideoMeeting.rtpServer.core._
import org.seekloud.VideoMeeting.rtpServer.protocol.ApiProtocol._
import akka.actor.typed.scaladsl.AskPattern._
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import net.sf.ehcache.search.query.QueryManager
import org.seekloud.VideoMeeting.rtpServer.core.DataStoreActor.AllStreamData
import org.seekloud.VideoMeeting.rtpServer.protocol.{ErrorRsp, SuccessRsp}
import org.slf4j.LoggerFactory

import scala.concurrent.Future

/**
  * Created by haoshuhan on 2019/8/28.
  */
trait ApiService extends BaseService with ServiceUtils with CirceSupport{
  import io.circe._
  import io.circe.generic.auto._

  def infoPage(allInfo: AllStreamData): String ={
    val pullInfo =
      allInfo.addressStreams.map{ info =>
          "<tr>" +
            s"<td style='border-left: 1px solid #555;border-top: 1px solid #555;padding: 5px;'>${info.address.host}</td>"+
            s"<td style='border-left: 1px solid #555;border-top: 1px solid #555;padding: 5px;'>${info.address.port}</td>"+
            "<td style='height:95px;padding:0;'>" +
              "<table style='border-collapse: collapse;border-spacing: 0;border: 0px;height:100%;width:100%;'>" +
                info.packageInfo.map{packageInf =>
                  "<tr>" +
                  s"<td style='border-left: 1px solid #555;border-top: 1px solid #555;border-bottom: 1px solid #555;padding: 5px;'><div style='width:160px;text-align:center'>${packageInf.liveId}</div></td>" +
                  s"<td style='border-left: 1px solid #555;border-top: 1px solid #555;border-bottom: 1px solid #555;padding: 5px;'><div style='width:80px;text-align:center'>${packageInf.packageLossInfo.lossScale60.formatted("%.4f")}</div></td>" +
                  s"<td style='border-left: 1px solid #555;border-top: 1px solid #555;border-bottom: 1px solid #555;padding: 5px'><div style='width:80px;text-align:center'>${packageInf.packageLossInfo.throwScale60.formatted("%.4f")}</div></td>" +
                  s"<td style='border-left: 1px solid #555;border-top: 1px solid #555;border-bottom: 1px solid #555;padding: 5px'><div style='width:80px;text-align:center'>${packageInf.packageLossInfo.lossScale10.formatted("%.4f")}</div></td>" +
                  s"<td style='border-left: 1px solid #555;border-top: 1px solid #555;border-bottom: 1px solid #555;padding: 5px'><div style='width:80px;text-align:center'>${packageInf.packageLossInfo.throwScale10.formatted("%.4f")}</div></td>" +
                  s"<td style='border-left: 1px solid #555;border-top: 1px solid #555;border-bottom: 1px solid #555;padding: 5px'><div style='width:80px;text-align:center'>${packageInf.packageLossInfo.lossScale2.formatted("%.4f")}</div></td>" +
                  s"<td style='border: 1px solid #555;border-right:none;padding: 5px'><div style='width:80px;text-align:center;'>${packageInf.packageLossInfo.throwScale2.formatted("%.4f")}</div></td>" +
                  "</tr>"
                }.mkString("") +
              "</table>" +
            "</td>" +
            "<td  style='padding:0;height:95px;'>" +
              "<table  style='border-collapse: collapse;border-spacing: 0;border: 0px;height: 100%;width:100%;'>"+
                "<tr>" +
                 s"<td style='border-left: 1px solid #555;border-top: 1px solid #555;border-bottom: 1px solid #555;padding: 5px'><div style='width:80px;text-align:center'>${info.bandwidthInfo.in10s}</div></td>" +
                  s"<td style='border-left: 1px solid #555;border-top: 1px solid #555;border-bottom: 1px solid #555;padding: 5px'><div style='width:80px;text-align:center'>${info.bandwidthInfo.in3s}</div></td>" +
                  s"<td style='border: 1px solid #555;border-right:none;'><div style='width:80px;text-align:center'>${info.bandwidthInfo.in1s}</div></td>" +
                "</tr>" +
              "</table>" +
            "</td>" +
          "</tr>"
      }.mkString("")

    val pushInfo =
      allInfo.streamDetail.map{info =>
          "<tr>" +
            s"<td style='border-left: 1px solid #555;border-top: 1px solid #555;padding: 5px;'>${info.ip}</td>" +
            s"<td style='border-left: 1px solid #555;border-top: 1px solid #555;padding: 5px;'>${info.port}</td>" +
            s"<td style='border-left: 1px solid #555;border-top: 1px solid #555;padding: 5px;'>${info.liveId}</td>" +
            s"<td style='border-left: 1px solid #555;border-top: 1px solid #555;padding: 5px;'>${info.ssrc}</td>" +
            "<td style='padding:0;height:95px'>" +
              "<table  style='border-collapse: collapse;border-spacing: 0;border: 0px;width: 100%;height: 100%;'>" +
                "<tr>" +
                  s"<td style='border-left: 1px solid #555;border-top: 1px solid #555;border-bottom: 1px solid #555;padding: 5px'><div style='width:80px;text-align:center'>${info.bandwidth.filter(_._1 == 10).head._2}</div></td>" +
                  s"<td style='border-left: 1px solid #555;border-top: 1px solid #555;border-bottom: 1px solid #555;padding: 5px'><div style='width:80px;text-align:center'>${info.bandwidth.filter(_._1 == 3).head._2}</div></td>" +
                  s"<td style='border: 1px solid #555;border-right:none;padding: 5px'><div style='width:80px;border-right:none;text-align:center'>${info.bandwidth.filter(_._1 == 1).head._2}</div></td>" +
                "</tr>" +
              "</table>" +
            "</td>" +
            s"<td style='border-left: 1px solid #555;border-top: 1px solid #555;padding: 5px;'>${info.delay}</td>"+
          "</tr>"

      }.mkString("")


      "<div style='margin: 20px auto;line-height: 20px'>"+
        "<h1>拉流信息</h1>"+
        "<table style='border-collapse: collapse;border-spacing: 0;border: 1px solid #555;'>" +
          "<tr>" +
            "<th style='border-left: 1px solid #555;border-top: 1px solid #555;padding: 5px'>IP</th>" +
            "<th style='border-left: 1px solid #555;border-top: 1px solid #555;padding: 5px'>端口</th>" +
            "<th style='border-left: 1px solid #555;border-top: 1px solid #555;padding:0px;'>" +
              "<div style='border-bottom:1px solid #555;'>丢包率</div>" +
              "<div>" +
                "<div style='text-align:center;width:160px;border-right:1px solid #555;padding: 5px;display:inline-block;'>liveId</div>" +
                "<div style='text-align:center;width:80px;border-right:1px solid #555;padding: 5px;display:inline-block;'>60s(总)</div>" +
                "<div style='text-align:center;width:80px;border-right:1px solid #555;padding: 5px;display:inline-block;'>60s(乱序)</div>" +
                "<div style='text-align:center;width:80px;border-right:1px solid #555;padding: 5px;display:inline-block;'>10s(总)</div>" +
                "<div style='text-align:center;width:80px;border-right:1px solid #555;padding: 5px;display:inline-block;'>10s(乱序)</div>" +
                "<div style='text-align:center;width:80px;border-right:1px solid #555;padding: 5px;display:inline-block;'>2s(总)</div>" +
                "<div style='text-align:center;width:80px;padding: 5px;display:inline-block;'>2s(乱序)</div>" +
              "</div>" +
            "</th>" +
            "<th style='border-left: 1px solid #555;border-top: 1px solid #555;'>" +
              "<div style='border-bottom:1px solid #555;'>带宽(bit/s)</div>" +
              "<div>"+
                "<div style='text-align:center;width:80px;border-right:1px solid #555;padding: 5px;display:inline-block;'>10s</div>" +
                "<div style='text-align:center;width:80px;border-right:1px solid #555;padding: 5px;display:inline-block;'>3s</div>" +
                "<div style='text-align:center;width:80px;padding: 5px;display:inline-block;'>1s</div>" +
              "</div>" +
            "</th>" +
          "</tr>" +
            pullInfo +
        "</table>" +
      "</div>"+
      "<div style='margin: 20px auto;line-height: 20px'>" +
        "<h1>推流信息</h1>" +
        "<table style='border-collapse: collapse;border-spacing: 0;border: 1px solid #555;'>" +
          "<tr>" +
            "<th style='border-left: 1px solid #555;border-top: 1px solid #555;padding: 5px'>ip</th>" +
            "<th style='border-left: 1px solid #555;border-top: 1px solid #555;padding: 5px'>port</th>" +
            "<th style='border-left: 1px solid #555;border-top: 1px solid #555;padding: 5px'>liveId</th>" +
            "<th style='border-left: 1px solid #555;border-top: 1px solid #555;padding: 5px'>ssrc</th>" +
            "<th style='border-left: 1px solid #555;border-top: 1px solid #555;'>" +
              "<div style='border-bottom:1px solid #555;'>带宽(bit/s)</div>" +
              "<div>"+
                "<div style='text-align:center;width:80px;border-right:1px solid #555;padding: 5px;display:inline-block;'>10s</div>" +
                "<div style='text-align:center;width:80px;border-right:1px solid #555;padding: 5px;display:inline-block;'>3s</div>" +
                "<div style='text-align:center;width:80px;padding: 5px;display:inline-block;'>1s</div>" +
              "</div>" +
            "</th>" +
            "<th style='border-left: 1px solid #555;border-top: 1px solid #555;padding: 5px'>延时(ms)</th>" +
          "</tr>" +
          pushInfo +
        "<table>" +
      "</div>"
  }

  private val log = LoggerFactory.getLogger(this.getClass)


  private val getLiveInfo = (path("getLiveInfo") & post & pathEndOrSingleSlash) {
    dealGetReq {
      val rstF: Future[LiveInfo] = userManager ? UserManager.GenLiveIdAndLiveCode
      rstF.map {rst =>
        complete(GetLiveInfoRsp(rst))
      }.recover {
        case e: Exception =>
          log.info(s"getLiveInfo error.." + e.getMessage)
          complete(110001, s"getLiveInfo error.....$e")
      }
    }
  }

  private val stopStream = (path("stopStream") & post & pathEndOrSingleSlash){
    dealPostReq[StopStreamReq] { r =>
      val msg: Future[Boolean] = streamManager ? (StreamManager.StopStream(r.liveId, _))
      msg.map{m =>
        if(m){
          complete(SuccessRsp())
        }else{
          complete(ErrorRsp(110002, "该流不存在"))
        }
      }
    }
  }

//  private val getAllInfo = (path("getAllInfo") & get & pathEndOrSingleSlash){
//      val msg: Future[AllInfo] = queryInfoManager ? QueryInfoManager.GetAllInfo
//      dealFutureResult{
//        msg.map{m =>
//          complete(GetAllInfoRsp(m))
//        }.recover{
//          case e: Exception =>
//            log.info(s"getAllInfo error.." + e.getMessage)
//            complete(110002, s"getAllInfo error.....$e")
//        }
//    }
//  }

  private val allInfoPage = (path("allInfoPage") & get & pathEndOrSingleSlash){
    getFromResource("html/allInfoPage.html")
  }

  private val getAllInfo = (path("getAllInfo") & get & pathEndOrSingleSlash){
    val msg: Future[AllStreamData] = dataStoreActor ? DataStoreActor.NeedStreamData
    dealFutureResult{
      msg.map{m =>
       // complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, infoPage(m)))
       // complete(GetAllStreamRsp(AllStreamInfo(m.addressStreams, m.streamDetail)))
        complete(AllStreamInfo(m.addressStreams, m.streamDetail))
      }.recover{
        case e: Exception =>
          log.info(s"getAllInfo error.." + e.getMessage)
          complete(110002, s"getAllInfo error.....$e")
      }
    }
  }

  private val delayData = (path("delay") & post & pathEndOrSingleSlash){
    entity(as[Either[Error,DelayReq]]){
      case Left(e) =>
        log.error(s"$e")
        complete(ErrorRsp(110004, s"parse json error"))

      case Right(req) =>
        //        println(s"====rec req$req")
        dataStoreActor ! DataStoreActor.Delay(req.liveId, req.delay)
        complete(SuccessRsp())
    }
  }

  private val packageLossData = (path("packageLoss") & post & pathEndOrSingleSlash){
//    dealPostReq[PackageLossReq]{ req =>
//        dataStoreActor ! DataStoreActor.PackageLoss(req.packageLossMap)
//        Future.successful(complete(SuccessRsp()))
//    }
    entity(as[Either[Error,PackageLossReq]]){
      case Left(e) =>
        log.error(s"$e")
        complete(ErrorRsp(110003, s"parse json error"))

      case Right(req) =>
//        println(s"====rec req$req")
        dataStoreActor ! DataStoreActor.PackageLoss(req.clientId, req.packageLossMap)
        complete(SuccessRsp())

    }
  }

  val apiRoutes = pathPrefix("api") {
    getLiveInfo ~ stopStream ~  getAllInfo ~ packageLossData ~delayData ~allInfoPage
  }

}
