package org.seekloud.VideoMeeting.processor.stream

import java.nio.channels.Pipe

class PipeStream {

  private val pipe = Pipe.open()
  private val sink = pipe.sink()
  private val source = pipe.source()

  def getSink = sink
  def getSource = source

}
