package org.seekloud.VideoMeeting.processor.utils

import java.lang.management.ManagementFactory
import java.lang.management.OperatingSystemMXBean
import java.lang.management.ThreadMXBean


object CpuUtils {
  private var osMxBean:OperatingSystemMXBean = _
  private var threadBean:ThreadMXBean = _
  private var preTime = System.nanoTime
  private var preUsedTime = 0.0

  import java.lang.management.ManagementFactory

  def init() = {
    osMxBean = ManagementFactory.getOperatingSystemMXBean
    threadBean = ManagementFactory.getThreadMXBean
  }

  def getProcessCpu() : Double = {
    var totalTime = 0.0
    for (id <- threadBean.getAllThreadIds) {
      totalTime += threadBean.getThreadCpuTime(id)
    }
    val curtime = System.nanoTime
    val usedTime = totalTime - preUsedTime
    val totalPassedTime = curtime - preTime
    preTime = curtime
    preUsedTime = totalTime
    (usedTime.toDouble / totalPassedTime / osMxBean.getAvailableProcessors) * 100
  }

  @throws[Exception]
  def main(args: Array[String]): Unit = {
    init()
    var i = 0
      new Thread(() => {
        def foo() = {
          while ( true) {
            var bac = 1000000
            bac = bac >> 1
          }
        }
        foo()
      }).start()

    new Thread(() => {
      def foo() = {
        while ( true) {
          var bac = 1000000
          bac = bac >> 1
        }
      }
      foo()
    }).start()

    new Thread(() => {
      def foo() = {
        while ( true) {
          var bac = 1000000
          bac = bac >> 1
        }
      }
      foo()
    }).start()


    while (true) {
      Thread.sleep(5000)
      println(getProcessCpu())
    }
  }
}
