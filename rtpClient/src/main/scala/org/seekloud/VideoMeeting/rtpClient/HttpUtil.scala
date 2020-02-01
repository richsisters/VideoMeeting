package org.seekloud.VideoMeeting.rtpClient

import java.io.File
import java.nio.charset.Charset
import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ssl.{ManagerFactoryParameters, TrustManager, X509TrustManager}
import io.netty.handler.ssl.{SslContext, SslContextBuilder}
import io.netty.handler.ssl.util.SimpleTrustManagerFactory
import org.asynchttpclient._
import org.asynchttpclient.request.body.multipart.FilePart
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future, Promise}

/**
  * User: Taoz
  * Date: 11/28/2016
  * Time: 17:28 PM
  *
  * update by zhangtao 2017-01-01 23:54
  * 1, remove unused import "akka.stream.Materializer"
  * 2, change "implicit val executor" to implicit in the function.
  * 3, you can import HttpUtil.Imports to use the api.
  *
  * update by zhangtao 2017-04-04 21:51
  * 1, set request charset in postJsonRequestSend
  *
  * update by hong 2017-08-09 21:10
  * 1, add parm addHeader in postJsonRequestSend,getRequestSend
  */

object HttpUtil {
  val config = {
    val build = new DefaultAsyncHttpClientConfig.Builder()
    val sslBuilder = SslContextBuilder.forClient()
    val tm = new TmFactory()
    val sslContext = sslBuilder.trustManager(tm).build()
    build.setSslContext(sslContext).build()
    build.setReadTimeout(200 * 1000).build()
  }

  private val ahClientImp: DefaultAsyncHttpClient = new DefaultAsyncHttpClient(config)
  private val log = LoggerFactory.getLogger(this.getClass)

  object Imports extends HttpUtil

  implicit class AhcToScala[T](reqBuilder: BoundRequestBuilder) {

    def scalaExecute(): Future[Response] = {
      import org.asynchttpclient.AsyncCompletionHandler
      val result = Promise[Response]()
      reqBuilder.execute(new AsyncCompletionHandler[Response]() {
        override def onCompleted(response: Response): Response = {
          result.success(response)
          response
        }
        override def onThrowable(t: Throwable): Unit = {
          result.failure(t)
        }
      })
      result.future
    }
  }
}

trait HttpUtil {


  import HttpUtil._

  //implicit val executor: ExecutionContext

  private val ahClient: DefaultAsyncHttpClient = ahClientImp

  import collection.JavaConverters._

  private def parseResp(response: Response, charset: Charset) = {
    val body = new String(response.getResponseBodyAsBytes, charset)
//    log.debug("getRequestSend response headers:" + response.getHeaders)
//    log.debug("getRequestSend response body:" + body)
    if (response.getStatusCode != 200) {
      val uri = response.getUri
      val bodyLength = body.length
      val msg = s"getRequestSend http failed url = $uri, status = ${response.getStatusCode}, text = ${response.getStatusText}, body = ${body.substring(0, Math.min(bodyLength, 1024))}"
      log.warn(msg)
      response.getStatusText
    }
    else{
      body
    }
  }

  private def executeRequest(
    methodName: String,
    request: BoundRequestBuilder,
    charset: Charset
  )(implicit executor: ExecutionContext) = {
    request.scalaExecute().map { response =>
      Right(parseResp(response, charset))
    }.recover { case e: Throwable => Left(e) }
  }

  def postJsonRequestSend(
    methodName: String,
    url: String,
    parameters: List[(String, String)],
    jsonStr: String,
    headers:List[(String,String)] = List(("Content-Type","application/json")),
    charsetName: String = "UTF-8"
  )(implicit executor: ExecutionContext): Future[Either[Throwable, String]] = {
//    log.info("Post Request [" + methodName + "] Processing...")
//    log.debug(methodName + " url=" + url)
//    log.debug(methodName + " parameters=" + parameters)
//    log.debug(methodName + " postData=" + jsonStr)
    val cs = Charset.forName(charsetName)
    val request = ahClient.
      preparePost(url).
      setFollowRedirect(true).
      setRequestTimeout(400 * 1000).
      setCharset(cs).
      addQueryParams(parameters.map { kv => new Param(kv._1, kv._2) }.asJava).
//      addHeader("Content-Type", "application/json").
      setBody(jsonStr)
    headers.foreach(h => request.addHeader(h._1,h._2))
    executeRequest(methodName, request, cs)
  }

  def getRequestSend(
    methodName: String,
    url: String,
    parameters: List[(String, String)],
    headers:List[(String,String)] = Nil,
    responseCharsetName: String = "UTF-8"
  )(implicit executor: ExecutionContext): Future[Either[Throwable, String]] = {
    log.info("Get Request [" + methodName + "] Processing...")
    log.debug(methodName + " url=" + url)
    log.debug(methodName + " parameters=" + parameters)
    val request = ahClient.
      prepareGet(url).
      setFollowRedirect(true).
      setRequestTimeout(20 * 1000).
      addQueryParams(parameters.map { kv => new Param(kv._1, kv._2) }.asJava)
    val cs = Charset.forName(responseCharsetName)
    headers.foreach(h => request.addHeader(h._1,h._2))
    executeRequest(methodName, request, cs)
  }

  def postImgRequestSend(
                          methodName: String,
                          url: String,
                          parameters: List[(String, String)],
                          //                          img: java.io.File,
                          img: Array[Byte],
                          charsetName: String = "UTF-8",
                          isLog: Boolean = false
                        )(implicit executor: ExecutionContext): Future[Either[Throwable, String]] = {
    if (isLog) {
      log.info("Post Request [" + methodName + "] Processing...")
      log.debug(methodName + " url=" + url)
      log.debug(methodName + " parameters=" + parameters)
      log.debug(methodName + " postData=" + img)
    }

    val cs = Charset.forName(charsetName)
    ahClient.
      preparePost(url)
    val request = ahClient.
      preparePost(url).
      setFollowRedirect(true).
      setRequestTimeout(30 * 1000).
      setCharset(cs).
      //      addQueryParams(parameters.map { kv => new Param(kv._1, kv._2) }.asJava).
      //      addHeader("Content-Type", "image/gif").
      setBody(img)
    executeRequest(methodName, request, cs)
  }

  def postXmlRequestSend(
                           methodName: String,
                           url: String,
                           xmlStr: String,
                           parameters: List[(String, String)] = Nil,
                           charsetName: String = "UTF-8"
                         )(implicit executor: ExecutionContext): Future[Either[Throwable, String]] = {
    log.info("Post Request [" + methodName + "] Processing...")
    log.debug(methodName + " url=" + url)
    log.debug(methodName + " parameters=" + parameters)
    log.debug(methodName + " postData=" + xmlStr)
    val cs = Charset.forName(charsetName)
    val request = ahClient.
      preparePost(url).
      setFollowRedirect(true).
      setRequestTimeout(20 * 1000).
      setCharset(cs).
      addQueryParams(parameters.map { kv => new Param(kv._1, kv._2) }.asJava).
      addHeader("Content-Type", "application/xml").
      setBody(xmlStr)
    executeRequest(methodName, request, cs)
  }

  def postFileRequestSend(
                           methodName: String,
                           url: String,
                           parameters: List[(String, String)],
                           file: File,
                           fileName: String,
                           responseCharsetName: String = "UTF-8"
                         )(implicit executor: ExecutionContext): Future[Either[Throwable, String]] = {
    log.info("Post Request [" + methodName + "] Processing...")
    log.debug(methodName + " url=" + url)
    log.debug(methodName + " parameters=" + parameters)
    log.debug(methodName + " postData=" + file.getName)

    val request = ahClient.
      preparePost(url).
      setFollowRedirect(true).
      setRequestTimeout(20 * 1000).
      addQueryParams(parameters.map { kv => new Param(kv._1, kv._2) }.asJava).
      addBodyPart(new FilePart("fileUpload", file, null, null, fileName))
    val cs = Charset.forName(responseCharsetName)
    executeRequest(methodName, request, cs)
  }

}


private class TmFactory extends SimpleTrustManagerFactory{
  val x509TM = new X509TrustManager {
    override def getAcceptedIssuers: Array[X509Certificate] = null

    override def checkClientTrusted(x509Certificates: Array[X509Certificate], s: String):Unit = {}

    override def checkServerTrusted(x509Certificates: Array[X509Certificate], s: String):Unit = {}

  }

  override def engineGetTrustManagers(): Array[TrustManager] = Array[TrustManager](x509TM)

  override def engineInit(keyStore: KeyStore): Unit = {}

  override def engineInit(managerFactoryParameters: ManagerFactoryParameters): Unit = {}
}