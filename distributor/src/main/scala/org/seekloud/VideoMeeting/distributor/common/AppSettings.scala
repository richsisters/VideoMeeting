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

package org.seekloud.VideoMeeting.distributor.common

import java.util.concurrent.TimeUnit

import org.seekloud.VideoMeeting.distributor.utils.SessionSupport.SessionConfig
import com.typesafe.config.{Config, ConfigFactory}
import org.slf4j.{Logger, LoggerFactory}


/**
  * User: yuwei
  * Date: 7/15/2019
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


  val log: Logger = LoggerFactory.getLogger(this.getClass)
  val config: Config = ConfigFactory.parseResources("application.conf").withFallback(ConfigFactory.load())

  val appConfig: Config = config.getConfig("app")

  val projectVersion: String = appConfig.getString("projectVersion")
  val httpInterface: String = appConfig.getString("http.interface")
  val httpPort: Int = appConfig.getInt("http.port")

  val fileLocation: String = appConfig.getString("fileLocation")
  val dashLocation: String = appConfig.getString("dashLocation")
  val hlsLocation: String = appConfig.getString("hlsLocation")
  val recordLocation: String = appConfig.getString("recordLocation")
  val indexPath: String = appConfig.getString("indexPath")
  val isTest: Boolean = appConfig.getBoolean("isTest")

  val slickConfig: Config = config.getConfig("slick.db")
  val slickUrl: String = slickConfig.getString("url")
  val slickUser: String = slickConfig.getString("user")
  val slickPassword: String = slickConfig.getString("password")
  val slickMaximumPoolSize: Int = slickConfig.getInt("maximumPoolSize")
  val slickConnectTimeout: Int = slickConfig.getInt("connectTimeout")
  val slickIdleTimeout: Int = slickConfig.getInt("idleTimeout")
  val slickMaxLifetime: Int = slickConfig.getInt("maxLifetime")

  
  val sessionConfig: SessionConfig = {
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
}
