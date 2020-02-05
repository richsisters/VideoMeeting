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
package org.seekloud.VideoMeeting.roomManager.common

import java.util.concurrent.TimeUnit
import com.typesafe.config.{Config, ConfigFactory}
import org.seekloud.VideoMeeting.roomManager.utils.SessionSupport.SessionConfig
import org.slf4j.LoggerFactory
import scala.collection.JavaConverters._

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

  val serverProtocol = appConfig.getString("server.protocol")
  val serverHost = appConfig.getString("server.host")
  val serverPort = appConfig.getString("server.port")
  val serverUrl = appConfig.getString("server.url")

  val httpInterface = appConfig.getString("http.interface")
  val httpPort = appConfig.getInt("http.port")
  val magicIp = appConfig.getString("magic.ip")
  val magicPort = appConfig.getInt("magic.port")

  val adminAccount = appConfig.getString("admin.account")
  val adminPassword = appConfig.getString("admin.password")

  val clientPath = appConfig.getString("clientPath")

  val processorIp = appConfig.getString("processor.ip")
  val processorPort = appConfig.getInt("processor.port")
  val distributorUseIp = appConfig.getBoolean("distributor.useIp")
  val distributorIp = appConfig.getString("distributor.ip")
  val distributorPort = appConfig.getString("distributor.port")
  val distributorDomain = appConfig.getString("distributor.domain")
  val rtpIp = appConfig.getString("rtp.ip")
  val rtpPort = appConfig.getInt("rtp.port")


  val dependenceConfig = config.getConfig("dependence")
  val rmConfig = dependenceConfig.getConfig("roomManager.config")
  val authCheck = rmConfig.getBoolean("authCheck")
  val tokenExistTime = rmConfig.getInt("tokenExistTime")
  val guestTokenExistTime = rmConfig.getInt("guestTokenExistTime")

  val emailConfig = dependenceConfig.getConfig("email.config")
  val emailHost = emailConfig.getString("host")
  val emailPort = emailConfig.getString("port")
  val emailDomain = emailConfig.getString("domain")
  val emailAddresserEmail = emailConfig.getString("addresserEmail")
  val emailAddresserPwd = emailConfig.getString("addresserPwd")

  val slickConfig = config.getConfig("slick.db")
  val slickUrl = slickConfig.getString("url")
  val slickUser = slickConfig.getString("user")
  val slickPassword = slickConfig.getString("password")
  val slickMaximumPoolSize = slickConfig.getInt("maximumPoolSize")
  val slickConnectTimeout = slickConfig.getInt("connectTimeout")
  val slickIdleTimeout = slickConfig.getInt("idleTimeout")
  val slickMaxLifetime = slickConfig.getInt("maxLifetime")

  val hestiaConfig = dependenceConfig.getConfig("hestia.config")
  val hestiaAppId = hestiaConfig.getString("appId")
  val hestiaSecureKey = hestiaConfig.getString("secureKey")
  val hestiaProtocol = hestiaConfig.getString("protocol")
  val hestiaImgProtocol = hestiaConfig.getString("imgProtocol")
  val hestiaHost = hestiaConfig.getString("host")
  val hestiaDomain = hestiaConfig.getString("domain")
  val hestiaPort = hestiaConfig.getInt("port")

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
  val guestIdPrefix = "guest"
  val userIdPrefix = "user"

  val tlsInfo = (appConfig.getString("tls.password"),appConfig.getString("tls.p12Path"))

  def generateRtpSdp(ip: String, audioPort: Int, videoPort: Int): String = {
    var rtpSdpOffer = "v=0\n"
    rtpSdpOffer += "o=- 0 0 IN IP4 " + ip + "\n"
    rtpSdpOffer += "s=KMS\n"
    rtpSdpOffer += "c=IN IP4 " + ip + "\n"
    rtpSdpOffer += "t=0 0\n"
    rtpSdpOffer += "m=audio " + audioPort + " RTP/AVP 97\n"
    rtpSdpOffer += "a=recvonly\n"
    rtpSdpOffer += "a=rtpmap:97 PCMU/8000\n"
    rtpSdpOffer += "a=fmtp:97 profile-level-id=1;mode=AAC-hbr;sizelength=13;indexlength=3;indexdeltalength=3;config=1508\n"
    rtpSdpOffer += "m=video " + videoPort + " RTP/AVP 96\n"
    rtpSdpOffer += "a=recvonly\n"
    rtpSdpOffer += "a=rtpmap:96 H264/90000\n"
    rtpSdpOffer += "a=rtcp-fb:96 goog-remb\n"
    rtpSdpOffer
  }
}
