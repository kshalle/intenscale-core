/*
 * SPDX-FileCopyrightText: 2016-2026 Intensivate, Inc.
 * SPDX-License-Identifier: LicenseRef-Intensivate-NC-1.0
 *
 * This file is part of the Intensivate CPU Core.
 *
 * Licensed under the Intensivate Non-Commercial Hardware Source
 * License v1.0. Commercial use requires a separate written license
 * from Intensivate, Inc.
 *
 * Full license: LICENSE.md
 * Patent notice: PATENTS.md
 */

package freechips.rocketchip.subsystem

import java.io._ 
import util.control.Breaks._

class File_Writer(strings: Array[String], file_sel: Boolean) {
  val file1 = "PrintStack.txt"
  val file2 = "Parameters.txt"

  if (!(new java.io.File("PrintStack.txt").isFile)) {
    val file1 = new File("PrintStack.txt")
  }

  if (!(new java.io.File("Parameters.txt").isFile)) {
    val file2 = new File("Parameters.txt")
  }
  
  if (file_sel) {
    val writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file1, false)))

    for(i <- strings){
      writer.write(i)
    }

    writer.close()
  }
  else{
    val writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file2, false)))

    for(i <- strings){
      writer.write(i)
    }

    writer.close()
  }
}

object config_tracker {
  var towrite = new Array[String](0)
  var stacktrace = new Array[String](0)
  var setWriteTxt = false

  def write_file(){
    if (setWriteTxt){
      val file_write  = new File_Writer(config_tracker.towrite, false)
      val file_write1 = new File_Writer(config_tracker.stacktrace, true)
    }
  }
}

object printParam {
  def ParamInt(value: Int, str: String): Int = {
    config_tracker.towrite = config_tracker.towrite :+ str
    config_tracker.towrite = config_tracker.towrite :+ "Value: "
    config_tracker.towrite = config_tracker.towrite :+ value.toString()
    config_tracker.towrite = config_tracker.towrite :+ "\n"
    
    val elements = Thread.currentThread().getStackTrace()

    config_tracker.towrite = config_tracker.towrite :+ elements(2).toString()
    config_tracker.towrite = config_tracker.towrite :+ "\n\n"
    
    config_tracker.stacktrace = config_tracker.stacktrace :+ str
    config_tracker.stacktrace = config_tracker.stacktrace :+ "\n"

    for(element <- elements){
      config_tracker.stacktrace = config_tracker.stacktrace :+ element.toString()
      config_tracker.stacktrace = config_tracker.stacktrace :+ "\n"
    }
      
    config_tracker.stacktrace = config_tracker.stacktrace :+ "\n\n"
    config_tracker.write_file()

    value
  }

  def ParamBool(value: Boolean, str: String): Boolean = {
    config_tracker.towrite = config_tracker.towrite :+ str
    config_tracker.towrite = config_tracker.towrite :+ " Value: "
    config_tracker.towrite = config_tracker.towrite :+ value.toString()
    config_tracker.towrite = config_tracker.towrite :+ "\n"
    
    val elements = Thread.currentThread().getStackTrace()

    config_tracker.towrite = config_tracker.towrite :+ elements(2).toString()
    config_tracker.towrite = config_tracker.towrite :+ "\n\n"
    
    config_tracker.stacktrace = config_tracker.stacktrace :+ str
    config_tracker.stacktrace = config_tracker.stacktrace :+ "\n"

    for(element <- elements){
      config_tracker.stacktrace = config_tracker.stacktrace :+ element.toString()
      config_tracker.stacktrace = config_tracker.stacktrace :+ "\n"
    }
      
    config_tracker.stacktrace = config_tracker.stacktrace :+ "\n\n"
    config_tracker.write_file()

    value
  }
}

