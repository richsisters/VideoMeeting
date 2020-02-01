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

package org.seekloud.VideoMeeting.processor.utils

import org.apache.commons.codec.digest.DigestUtils

import scala.util.Random

/**
  * User: Taoz
  * Date: 7/8/2015
  * Time: 8:42 PM
  */
object SecureUtil {

  val random = new Random(System.currentTimeMillis())

  val chars = Array(
    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
    'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
    'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z'
  )


  final case class PostEnvelope(
                                 appId: String,
                                 sn: String,
                                 timestamp: String,
                                 nonce: String,
                                 data: String,
                                 signature: String
                               )

  def checkPostEnvelope(envelope: PostEnvelope, secureKey: String): Boolean = {
    import envelope._
    val params = List(appId, sn, timestamp, nonce, data)
    checkSignature(params, signature, secureKey)
  }


  def genPostEnvelope(appId: String, sn: String, data: String, secureKey: String): PostEnvelope = {
    val params = List(appId, sn, data)
    val (timestamp, nonce, signature) = generateSignatureParameters(params, secureKey)
    PostEnvelope(appId, sn, timestamp, nonce, data, signature)
  }


  def getSecurePassword(password: String, userId:String): String = {
    DigestUtils.sha1Hex(DigestUtils.md5Hex(password) + userId)
  }

  def checkSecurePwd(password: String, userId:String, sha1Pwd:String)={
    getSecurePassword(password,userId)==sha1Pwd
  }

  def checkSignature(parameters: List[String], signature: String, secureKey: String): Boolean = {
    generateSignature(parameters, secureKey) == signature
  }

  def generateSignature(parameters: List[String], secureKey: String): String = {
    val strSeq = (secureKey :: parameters).sorted.mkString("")
    //println(s"strSeq: $strSeq")
    DigestUtils.sha1Hex(strSeq)
  }

  def generateSignatureParameters(parameters: List[String], secureKey: String): (String, String, String) = {
    val timestamp = System.currentTimeMillis().toString
    val nonce = nonceStr(6)
    val pList = nonce :: timestamp :: parameters
    val signature = generateSignature(pList, secureKey)
    (timestamp, nonce, signature)
  }

  def nonceStr(length: Int): String = {
    val range = chars.length
    (0 until length).map { _ =>
      chars(random.nextInt(range))
    }.mkString("")
  }


  def checkStringSign(str: String, sign: String, secureKey: String): Boolean = {
    stringSign(str, secureKey) == sign
  }

  def stringSign(str: String, secureKey: String): String = {
    DigestUtils.sha1Hex(secureKey + str)
  }

  def appSafety={
    val now=System.currentTimeMillis()
    val appSessionKey=SecureUtil.nonceStr(6)+now
    val keyCode=SecureUtil.stringSign(SecureUtil.nonceStr(6),appSessionKey)
    val signature=SecureUtil.stringSign(keyCode,appSessionKey)
    (appSessionKey,keyCode,signature)
  }

  def main1(args: Array[String]) {

    val a=generateSignature(List("pyramidadmin","2017-11-2 15:58","du2017bai","41xy2p"),

      "dsacsodaux84fsdcs4wc32xm")

    println(a)

  }


}
