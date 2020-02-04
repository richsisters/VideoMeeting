package org.seekloud.VideoMeeting.distributor

import java.io.File
import java.util

import org.bytedeco.javacpp.Loader
//import org.seekloud.VideoMeeting.distributor.common.AppSettings.{fileLocation, indexPath, recordLocation}
//import org.seekloud.VideoMeeting.distributor.utils.CmdUtil

/**
  * User: xueganyuan
  * Date: 2019/10/23
  * Time: 17:38
  *
  * ProcessBuilder测试,m4s<->mp4
  */


object CmdTest2 {
  def main(args: Array[String]): Unit = {
    start124()

    val os = new File(System.getProperty("os.name"))
    println(os)
    //    println("hhh")


  }

  def start124(): Unit = {
    val ffmpeg = Loader.load(classOf[org.bytedeco.ffmpeg.ffmpeg])


    //          生成多码率文件
    //              val pb = new ProcessBuilder(ffmpeg,"-i",s"D:/Github/test--mp4_to_m4s/ieef.mp4",
    //                "-b:v:0","2496k","-b:a:0","64k","-s:v:0","1280x720",
    //                "-b:v:1","1216k","-b:a:1","64k","-s:v:1","720x480",
    //                "-b:v:2","896k","-b:a:2","64k","-s:v:2","640x360",
    //                "-map","0:v","-map","0:a","-map","0:v","-map","0:a","-map","0:v","-map","0:a",
    //                "-f","dash","-window_size","20","-extra_window_size","20","-hls_playlist","1",
    //                "-c:v","libx264","-c:a","aac","-hls_time","10","D:/Github/test--mp4_to_m4s/test1/index.mpd")
    //

    //          生成多码率文件,不生成m3u8
    //    val pb = new ProcessBuilder(ffmpeg, "-i", s"D:/Github/test--mp4_to_m4s/ieef.mp4",
    //      "-b:v:0", "2496k", "-b:a:0", "64k", "-s:v:0", "1280x720",
    //      "-b:v:1", "1216k", "-b:a:1", "64k", "-s:v:1", "720x480",
    //      "-b:v:2", "896k", "-b:a:2", "64k", "-s:v:2", "640x360",
    //      "-map", "0:v", "-map", "0:a", "-map", "0:v", "-map", "0:a", "-map", "0:v", "-map", "0:a",
    //      "-f", "dash", "-window_size", "20", "-extra_window_size", "20",
    //      "D:/Github/test--mp4_to_m4s/test2/index.mpd")

    //          生成多码率文件,不生成m3u8，不生成多码率
    //    val pb = new ProcessBuilder(ffmpeg, "-i", s"D:/Github/test--mp4_to_m4s/ieef.mp4",
    //      "-b:v:0", "896k", "-b:a:0", "64k", "-s:v:0", "640x360",
    //      "-map", "0:v", "-map", "0:a","-map", "0:v", "-map", "0:a", "-map", "0:v", "-map", "0:a", "-map", "0:v", "-map", "0:a",
    //      "-f", "dash", "-window_size", "20", "-extra_window_size", "20",
    //      "D:/Github/test--mp4_to_m4s/test6/index.mpd")


    //      val pb = new ProcessBuilder(ffmpeg, "-i", s"udp://127.0.0.1:$port","-b:v","1M",s"$dashLocation$roomId/index.mpd", "-f", "hls","-b:v","1M", "-hls_time", "3", "-segment_list_flags",
    //        "+live","-hls_list_size", "20",s"$hlsLocation$roomId/index.m3u8")
    //      val pb = new ProcessBuilder(ffmpeg, "-i", s"D:\\Github\\test--mp4_to_m4s\\ieef.mp4",
    //        "-b:v", "1M", "-f", "dash", "-window_size", "20", "-extra_window_size", "20", "-hls_playlist", "1", s"D:\\Github\\test--mp4_to_m4s\\index.mpd"
    //        , "-b:v", "1M", s"D:\\Github\\test--mp4_to_m4s\\record.ts")

    //    val pb = new ProcessBuilder(ffmpeg, "-i", s"D:\\Github\\test--mp4_to_m4s\\test1\\init-stream0.m4s",
    //      "-i", s"D:\\Github\\test--mp4_to_m4s\\test1\\chunk-stream0*.m4s",
    //      "-codec","copy","D:\\Github\\test--mp4_to_m4s\\ieef2.mp4",
    //    )

    //        合成mp4
    val pb = new ProcessBuilder(ffmpeg, "-i", s"D:\\Github\\test--mp4_to_m4s\\test5\\init-stream0.m4s",
      "-i", s"D:\\Github\\test--mp4_to_m4s\\test5\\chunk-stream0-00001.m4s",
      "-i", s"D:\\Github\\test--mp4_to_m4s\\test5\\chunk-stream0-00002.m4s",
      "-i", s"D:\\Github\\test--mp4_to_m4s\\test5\\chunk-stream0-00003.m4s",
      "-i", s"D:\\Github\\test--mp4_to_m4s\\test5\\chunk-stream0-00004.m4s",
      "-codec", "copy", "D:\\Github\\test--mp4_to_m4s\\ieef4.mp4",
    )

    //    val pb = new ProcessBuilder("type", s"D:\\Github\\test--mp4_to_m4s\\test5\\stream0*.m4s",">>","fffff.m4s"
    //    )


    //    val pb = new ProcessBuilder("copy", s"D:/Github/test--mp4_to_m4s/test5/ff.mp4", s"D:/Github/test--mp4_to_m4s/test5/ff.mp4",
    //      "-i", s"D:\\Github\\test--mp4_to_m4s\\test5\\ff2.mp4",
    //      "-c:v", "copy", "-c:a", "copy", "D:\\Github\\test--mp4_to_m4s\\ieef2.mp4",
    //    )

    //        合成mp4
    //    val pb = new ProcessBuilder(ffmpeg, "-i", s"D:\\Github\\test--mp4_to_m4s\\test6\\fff1.mp4",
    //      "-i", s"D:\\Github\\test--mp4_to_m4s\\test6\\fff.mp4",
    //      "-codec","copy", "D:\\Github\\test--mp4_to_m4s\\ieef3.mp4",
    //    )


    //    val pb = new ProcessBuilder(ffmpeg, "-i", s"D:\\Github\\test--mp4_to_m4s\\test5\\init-stream0.m4s",
    //      "-i", s"D:\\Github\\test--mp4_to_m4s\\test5\\init-stream1.m4s",
    //      "-i", s"D:\\Github\\test--mp4_to_m4s\\test5\\chunk-stream0-00001.m4s",
    //      "-codec","copy","D:\\Github\\test--mp4_to_m4s\\ieef2.mp4",
    //    )

    val process = pb.inheritIO().start()
    //      this.process = process
    //      val process = pb.start()

    //      val  p =new ProcessBuilder("ipconfig","/all").start()
    //            new ProcessBuilder(util.Arrays.asList("ping","www.baidu.com")).start()
    //      val pb = new ProcessBuilder(ffmpeg,"-i",s"D:/helloMedia/0.mp4",
    //        "-b:v:0","2496k","-b:a:0","64k","-s:v:0","1280x720",
    //        "-b:v:1","1216k","-b:a:1","64k","-s:v:1","720x480",
    //        "-b:v:2","896k","-b:a:2","64k","-s:v:2","640x360",
    //        "-map","0:v","-map","0:a","-map","0:v","-map","0:a","-map","0:v","-map","0:a",
    //        "-f","hls",
    //        "-var_stream_map","\"v:0,a:0 v:1,a:1 v:2,a:2\"",
    //        "-c:v","libx264","-c:a","aac",
    //        "-hls_time","10","-master_pl_name","master.m3u8",
    //        "D:/test/out%v/out.m3u8")
    //      println(p)


    //      val process2 =new ProcessBuilder("notepad.exe").start()

    //      val processBuilder = new ProcessBuilder
    //
    //      processBuilder.command("notepad.exe")
    //
    //      val process = processBuilder.start
    //
    //      val ret = process.waitFor
    //
    //      println("Program exited with code: %d", ret)


  }


}


