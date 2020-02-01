package org.seekloud.VideoMeeting.pcClient.utils

import java.io.File
import java.nio.charset.Charset
import org.asynchttpclient._
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future, Promise}
import org.asynchttpclient.request.body.multipart.FilePart

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
  */

object HttpUtil {

  //skip ssl check.
  /*  val config: AsyncHttpClientConfig = {
      val builder = new DefaultAsyncHttpClientConfig.Builder()
      val sslBuilder = SslContextBuilder.forClient()
      sslBuilder.trustManager(new TmFactory())
      val sslContext = sslBuilder.build()
      builder.setSslContext(sslContext)
      builder.build()
    }

    private val ahClientImp: DefaultAsyncHttpClient = new DefaultAsyncHttpClient(config)*/
  private val ahClientImp: DefaultAsyncHttpClient = new DefaultAsyncHttpClient()

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

  private def parseResp(response: Response, charset: Charset, needLogRsp: Boolean = true) = {
    val body = new String(response.getResponseBodyAsBytes, charset)
    if (needLogRsp) {
      log.debug("getRequestSend response headers:" + response.getHeaders)
      log.debug("getRequestSend response body:" + body)
    }
    //    log.debug("getRequestSend response headers:" + response.getHeaders)
    //    log.debug("getRequestSend response body:" + body)
    if (response.getStatusCode != 200) {
      val uri = response.getUri
      val bodyLength = body.length
      val msg = s"getRequestSend http failed url = $uri, status = ${response.getStatusCode}, text = ${response.getStatusText}, body = ${body.substring(0, Math.min(bodyLength, 1024))}"
      log.warn(msg)
    }
    body
  }

  private def executeRequest(
    methodName: String,
    request: BoundRequestBuilder,
    charset: Charset,
    needLogRsp: Boolean = true
  )(implicit executor: ExecutionContext) = {
    request.scalaExecute().map { response =>
      Right(parseResp(response, charset, needLogRsp))
    }.recover { case e: Throwable => Left(e) }
  }

  def postJsonRequestSend(
    methodName: String,
    url: String,
    parameters: List[(String, String)],
    jsonStr: String,
    charsetName: String = "UTF-8",
    timeOut: Int = 20 * 1000,
    needLogRsp: Boolean = true
  )(implicit executor: ExecutionContext): Future[Either[Throwable, String]] = {
    if (needLogRsp) {
      log.info("Post Request [" + methodName + "] Processing...")
      log.debug(methodName + " url=" + url)
      log.debug(methodName + " parameters=" + parameters)
      log.debug(methodName + " postData=" + jsonStr)
    }
    val cs = Charset.forName(charsetName)
    ahClient.
      preparePost(url)
    val request = ahClient.
      preparePost(url).
      setFollowRedirect(true).
      setRequestTimeout(timeOut).
      setCharset(cs).
      addQueryParams(parameters.map { kv => new Param(kv._1, kv._2) }.asJava).
      addHeader("Content-Type", "application/json").
      setBody(jsonStr)
    executeRequest(methodName, request, cs, needLogRsp)
  }

  def getRequestSend(
    methodName: String,
    url: String,
    parameters: List[(String, String)],
    responseCharsetName: String = "UTF-8",
    needLogRsp: Boolean = true
  )(implicit executor: ExecutionContext): Future[Either[Throwable, String]] = {
    if (needLogRsp) {
      log.info("Get Request [" + methodName + "] Processing...")
      log.debug(methodName + " url=" + url)
      log.debug(methodName + " parameters=" + parameters)
    }
    val request = ahClient.
      prepareGet(url).
      setFollowRedirect(true).
      setRequestTimeout(20 * 1000).
      addQueryParams(parameters.map { kv => new Param(kv._1, kv._2) }.asJava)
    val cs = Charset.forName(responseCharsetName)
    executeRequest(methodName, request, cs, needLogRsp)
  }

  def getImageContent(
    methodName: String,
    url: String,
    parameters: List[(String, String)],
    responseCharsetName: String = "UTF-8",
    needLogRsp: Boolean = true
  )(implicit executor: ExecutionContext): Future[Either[Throwable, Array[Byte]]] = {
    if (needLogRsp) {
      log.info("Get Request [" + methodName + "] Processing...")
      log.debug(methodName + " url=" + url)
      log.debug(methodName + " parameters=" + parameters)
    }
    val request = try {
      ahClient.
        prepareGet(url).
        setFollowRedirect(true).
        setRequestTimeout(20 * 1000).
        addQueryParams(parameters.map { kv => new Param(kv._1, kv._2) }.asJava)
    }
    catch {
      case e : Exception =>
        log.info(s"get image content error! ${e.getMessage}")
        throw  e
    }
    val cs = Charset.forName(responseCharsetName)
    request.scalaExecute().map { response =>
      val body = response.getResponseBodyAsBytes
      if (needLogRsp) {
        log.debug("getRequestSend response headers:" + response.getHeaders)
        log.debug("getRequestSend response body:" + body)
      }
      if (response.getStatusCode != 200) {
        val uri = response.getUri
//        val bodyLength = body.length
        val msg = s"getRequestSend http failed url = $uri, status = ${response.getStatusCode}, text = ${response.getStatusText}"
        log.warn(msg)
      }
      Right(body)
    }.recover { case e: Throwable => Left(e) }
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
      //      setRequestTimeout(20 * 1000).
      setRequestTimeout(60 * 1000).
      addQueryParams(parameters.map { kv => new Param(kv._1, kv._2) }.asJava).
      addBodyPart(new FilePart("fileUpload", file, null, null, fileName))
    val cs = Charset.forName(responseCharsetName)
    executeRequest(methodName, request, cs)
  }


}


//class TmFactory extends SimpleTrustManagerFactory {
//
//  val tm = new X509TrustManager {
//    override def checkServerTrusted(x509Certificates: Array[X509Certificate], s: String): Unit = {}
//
//    override def checkClientTrusted(x509Certificates: Array[X509Certificate], s: String): Unit = {}
//
//    override def getAcceptedIssuers: Array[X509Certificate] = null
//  }
//
//  override def engineGetTrustManagers(): Array[TrustManager] = Array[TrustManager](tm)
//
//  override def engineInit(keyStore: KeyStore): Unit = {}
//
//  override def engineInit(managerFactoryParameters: ManagerFactoryParameters): Unit = {}
//
//
//}
