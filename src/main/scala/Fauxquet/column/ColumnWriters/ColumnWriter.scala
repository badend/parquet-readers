package main.scala.Fauxquet.column.ColumnWriters

import main.scala.Fauxquet.io.api.Binary

/**
  * Created by james on 1/26/17.
  */
trait ColumnWriter {
  def write(value: Int, repetitionLevel: Int, definitionLevel: Int)
  def write(value: Long, repetitionLevel: Int, definitionLevel: Int)
  def write(value: Boolean, repetitionLevel: Int, definitionLevel: Int)
  //def write(value: Array[Byte], repetitionLevel: Int, definitionLevel: Int)
  def write(value: Float, repetitionLevel: Int, definitionLevel: Int)
  def write(value: Double, repetitionLevel: Int, definitionLevel: Int)
  def write(value: Binary, repetitionLevel: Int, definitionLevel: Int)

  def writeNull(repetitionLevel: Int, definitionLevel: Int)

  def close(): Unit
  def flush(): Unit

  def bufferedSize: Long
}
