//  Copyright 2018 seekloud (https://github.com/seekloud)
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.

package org.seekloud.VideoMeeting.rtpServer.common

import java.util.concurrent.TimeUnit

import com.typesafe.config.{Config, ConfigFactory}
import org.seekloud.VideoMeeting.rtpServer.utils.SessionSupport.SessionConfig
import org.slf4j.LoggerFactory
import collection.JavaConverters._

/**
  * User: Taoz
  * Date: 9/4/2015
  * Time: 4:29 PM
  */
object AppSettings {

  private implicit class RichConfig(config: Config) {
    val noneValue = "none"

    def getOptionalString(path: String): Option[String] =
      if (config.getAnyRef(path) == noneValue) None
      else Some(config.getString(path))

    def getOptionalLong(path: String): Option[Long] =
      if (config.getAnyRef(path) == noneValue) None
      else Some(config.getLong(path))

    def getOptionalDurationSeconds(path: String): Option[Long] =
      if (config.getAnyRef(path) == noneValue) None
      else Some(config.getDuration(path, TimeUnit.SECONDS))
  }


  val log = LoggerFactory.getLogger(this.getClass)
  val config = ConfigFactory.parseResources("product.conf").withFallback(ConfigFactory.load())

  val appConfig = config.getConfig("app")


  val httpInterface = appConfig.getString("http.interface")
  val httpPort = appConfig.getInt("http.port")

  val receiverHost = appConfig.getString("receiver.host")
  val receiverPort = appConfig.getInt("receiver.port")

  val senderHost = appConfig.getString("sender.host")
  val senderPort = appConfig.getInt("sender.port")

  val tsStreamType = appConfig.getInt("payloadType.tsStream")
  val authPusher = appConfig.getInt("payloadType.authPusher")
  val authResponse = appConfig.getInt("payloadType.authResponse")
  val authRefuseResponse = appConfig.getInt("payloadType.authRefuseResponse")
  val pullStreamRequest = appConfig.getInt("payloadType.pullStreamRequest")
  val pullStreamResponse = appConfig.getInt("payloadType.pullStreamResponse")
  val pullStreamRefuseResponse = appConfig.getInt("payloadType.pullStreamRefuseResponse")
  val streamStopped = appConfig.getInt("payloadType.streamStopped")
  val stopPushingReq = appConfig.getInt("payloadType.stopPushingReq")
  val stopPushingRsp = appConfig.getInt("payloadType.stopPushingRsp")
  val getClientIdRequest = appConfig.getInt("payloadType.getClientIdRequest")
  val getClientIdResponse = appConfig.getInt("payloadType.getClientIdResponse")
  val pullStreamUserHeartbeat = appConfig.getInt("payloadType.pullStreamUserHeartbeat")
  val stopPullingReq = appConfig.getInt("payloadType.stopPullingReq")
  val stopPullingRsp = appConfig.getInt("payloadType.stopPullingRsp")
  val calcDelayReq = appConfig.getInt("payloadType.calcDelayRequest")
  val calcDelayRsp = appConfig.getInt("payloadType.calcDelayResponse")
  val subscriberRcvSSRC = appConfig.getInt("payloadType.subscriberRcvSSRC")

  val roomManagerConfig = config.getConfig("dependence.roomManager")
  val roomManagerProtocol = roomManagerConfig.getString("protocol")
  val roomManagerPort = roomManagerConfig.getInt("port")
  val roomManagerDomain = roomManagerConfig.getString("domain")
  val roomManagerUrl = roomManagerConfig.getString("url")
  val roomManagerHost = roomManagerConfig.getString("host")
  val roomManagerAppId = roomManagerConfig.getString("appId")
  val roomManagerSecureKey = roomManagerConfig.getString("secureKey")



  val slickConfig = config.getConfig("slick.db")
  val slickUrl = slickConfig.getString("url")
  val slickUser = slickConfig.getString("user")
  val slickPassword = slickConfig.getString("password")
  val slickMaximumPoolSize = slickConfig.getInt("maximumPoolSize")
  val slickConnectTimeout = slickConfig.getInt("connectTimeout")
  val slickIdleTimeout = slickConfig.getInt("idleTimeout")
  val slickMaxLifetime = slickConfig.getInt("maxLifetime")

  val essfMapKeyName = "essfMap"

  val sessionConfig = {
    val sConf = config.getConfig("session")
    SessionConfig(
      cookieName = sConf.getString("cookie.name"),
      serverSecret = sConf.getString("serverSecret"),
      domain = sConf.getOptionalString("cookie.domain"),
      path = sConf.getOptionalString("cookie.path"),
      secure = sConf.getBoolean("cookie.secure"),
      httpOnly = sConf.getBoolean("cookie.httpOnly"),
      maxAge = sConf.getOptionalDurationSeconds("cookie.maxAge"),
      sessionEncryptData = sConf.getBoolean("encryptData")
    )
  }

  val appSecureMap = {
    val appIds = appConfig.getStringList("client.appIds").asScala
    val secureKeys = appConfig.getStringList("client.secureKeys").asScala
    require(appIds.length == secureKeys.length, "appIdList.length and secureKeys.length not equel.")
    appIds.zip(secureKeys).toMap
  }

  val projectVersion = appConfig.getString("projectVersion")

//  val videoFilePath = appConfig.getString("fileSetting.videoFilePath")

//  val frameInterval = appConfig.getInt("frameInterval")

}
