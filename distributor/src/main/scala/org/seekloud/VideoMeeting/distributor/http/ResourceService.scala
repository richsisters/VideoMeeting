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

import akka.actor.{ActorSystem, Scheduler}
import akka.event.LoggingAdapter
import akka.http.scaladsl.model.headers.CacheDirectives.{`max-age`, public}
import akka.http.scaladsl.model.headers.`Cache-Control`
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import org.seekloud.VideoMeeting.distributor.common.AppSettings

import scala.concurrent.ExecutionContextExecutor

/**
  * User: yuwei
  * Date: 7/15/2019
  */
trait ResourceService {

  implicit val system: ActorSystem

  implicit val executor: ExecutionContextExecutor

  implicit val materializer: Materializer

  implicit val scheduler: Scheduler

  val log: LoggingAdapter


  private val resources = {
    pathPrefix("css") {
      extractUnmatchedPath { path =>
        getFromResourceDirectory("css")
      }
    } ~
    pathPrefix("dash"){
      extractUnmatchedPath { path =>
        getFromResourceDirectory("dash")
      }
    }~
    pathPrefix("js") {
      extractUnmatchedPath { path =>
        getFromResourceDirectory("js")
      }
    } ~
    pathPrefix("sjsout") {
      extractUnmatchedPath { path =>
        getFromResourceDirectory("sjsout")
      }
    } ~
    pathPrefix("img") {
      getFromResourceDirectory("img")
    } ~
    pathPrefix("dash") {
      getFromDirectory("/dash")
    } ~
    pathPrefix("test") {
      getFromDirectory("D:\\workstation\\sbt\\vigour\\logs\\test")
    } ~
      path("jsFile" / Segment / AppSettings.projectVersion) { name =>
        val jsFileName = name + ".js"
        if (jsFileName == "frontend-fastopt.js") {
          getFromResource(s"sjsout/$jsFileName")
        } else {
          getFromResource(s"js/$jsFileName")
        }
      }
  }

  //cache code copied from zhaorui.
  private val cacheSeconds = 24 * 60 * 60

  val resourceRoutes: Route = (pathPrefix("static") & get) {
    mapResponseHeaders { headers => `Cache-Control`(`public`, `max-age`(cacheSeconds)) +: headers } {
      encodeResponse(resources)
    }
  } ~ pathPrefix("html") {
    extractUnmatchedPath { path =>
      getFromResourceDirectory("html")
    }
  }


}
