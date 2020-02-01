package org.seekloud.VideoMeeting.roomManager.utils

import java.io.{File, FilenameFilter}

trait FileFilter extends FilenameFilter{
  override def accept(dir: File, name: String): Boolean = {
    true
  }

}

class WinFileFilter extends FilenameFilter{
    override def accept(dir: File, name: String): Boolean = {
      name.contains("win")
    }
}

class MacFileFilter extends FilenameFilter{
  override def accept(dir: File, name: String): Boolean = {
    name.contains("mac")
  }
}



