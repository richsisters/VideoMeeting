package org.seekloud.VideoMeeting.rtpServer.protocol

import org.seekloud.VideoMeeting.rtpServer.ptcl.protocol.Address

import scala.collection.mutable

/**
  * Created by haoshuhan on 2019/8/28.
  */
object ApiProtocol {
  case class LiveInfo(liveId: String, liveCode: String)

  case class GetLiveInfoRsp(
                           liveInfo: LiveInfo,
                           errCode: Int = 0,
                           msg: String = ""
                           )

  case class StopStreamReq(liveId: String)

  case class StreamInfoDetail(
                            liveId: String,
                            ssrc: Int,
                            ip: String,
                            port: Int,
                            bandwidth: List[(Int, Int)]
                          )

  case class StreamInfoDetailPlus(
                            liveId: String,
                            ssrc: Int,
                            ip: String,
                            port: Int,
                            bandwidth: List[(Int, Int)],
                            delay: Double
                          )

  case class AddressToStreams(clientId:Int, address: Address, streams: List[String])

  case class AllInfo(
                    pullStreamInfo: Option[List[AddressToStreams]],
                    streamInfo: Option[List[StreamInfoDetail]]
                  )

  case class GetAllInfoRsp(
                          allInfo: AllInfo,
                          errCode: Int = 0,
                          msg: String = ""
                        )
  case class StreamPackageLoss(liveId:String, packageLossInfo: PackageLossInfo)

  case class AddressPackageLoss(address:Address, packageInfo: List[StreamPackageLoss])

  case class AddressPackageInfo(address:Address, packageInfo: List[StreamPackageLoss], bandwidthInfo: BandwidthInfo)

  case class PackageLossInfo(lossScale60: Double, throwScale60: Double, lossScale10: Double,
            throwScale10: Double, lossScale2: Double, throwScale2: Double, lossTotal: Double)

  case class BandwidthInfo(in1s: Int, in3s: Int, in10s: Int)

  case class PackageLossReq(
                           clientId:Int,
                           packageLossMap:mutable.Map[String, PackageLossInfo]
                           )

  case class DelayReq(
                      liveId: String,
                      delay: Double
                      )

  case class AllStreamInfo(
                      pullStreamInfo: List[AddressPackageInfo],
                      streamInfo: List[StreamInfoDetailPlus]
                    )

  case class GetAllStreamRsp(
                            allStreamInfo: AllStreamInfo,
                            errCode: Int = 0,
                            msg: String = ""
                            )
}
