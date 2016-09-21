package main.scala.Fauxquet

import java.io.PrintWriter
import java.nio.charset.Charset
import java.nio.file.{Files, Paths}

/**
  * Created by james on 8/5/16.
  */
class FauxquetFile(val file: String) {
  type Schema = Vector[String]
  type Fields = Vector[String]

  def Schema(schema: List[String]) = schema.toVector

  val MAGIC = "PAR1".getBytes(Charset.forName("ASCII"))

  lazy val array = new SeekableArray[Byte](Files.readAllBytes(Paths.get(file)))
  lazy val table: Map[String, List[String]] = ???
  val fileMetaData: FileMetadata = new FileMetadata()
  var schema: Schema = _
  var fields: Fields = _

  def init() = {
    //val out = new PrintWriter("./resources/fauxquet_out.txt")
    val out = System.out

    if (!isParquetFile) throw new Error(s"$file is not a valid Parquet file.")

    fileMetaData read array

    array pos = 4

    for (i <- 1 until fileMetaData.schema.length) { //skip 'm'
      var valuesRead = 0L

      while (valuesRead < fileMetaData.numRows) {
        val pageHeader = new PageHeader
        pageHeader read array

        valuesRead += pageHeader.dataPageHeader.numValues

        val numToSkip = LittleEndianDecoder readInt array
        array.pos = array.pos + numToSkip

        var j = 0
        while (j < pageHeader.dataPageHeader.numValues) {
          fileMetaData.schema(i).Type match {
            case TType(0, "BOOLEAN") => out.println(LittleEndianDecoder readBool array)
            case TType(1, "INT32") => out.println(LittleEndianDecoder readInt array)
            case TType(2, "INT64") => out.println(LittleEndianDecoder readLong array)
            case TType(3, "INT96") => out.println(fileMetaData.schema(i).Type.value + " should be int96")
            case TType(4, "FLOAT") => out.println(LittleEndianDecoder readFloat array)
            case TType(5, "DOUBLE") => out.println(LittleEndianDecoder readDouble array)
            case TType(6, "BYTE_ARRAY") => out.println(LittleEndianDecoder readString array)
            case TType(7, "FIXED_LEN_BYTE_ARRAY") => out.println(LittleEndianDecoder readFixedLengthString(array, 8)) //figure out length
          }

          j = j + 1
        }

        //println(array pos)
        //array pos = array.pos + pageHeader.compressedPageSize
      }
    }

    //println("Done reading fileMetaData")
  }

  def isParquetFile: Boolean = {
    val l = array length

    val MAGIC = "PAR1".getBytes(Charset.forName("ASCII"))

    val footerLengthIndex = l - 4 - MAGIC.length

    val footerLength = {
      val x1 = array(footerLengthIndex)
      val x2 = array(footerLengthIndex + 1)
      val x3 = array(footerLengthIndex + 2)
      val x4 = array(footerLengthIndex + 3)

      if ((x1 | x2 | x3 | x4) < 0) throw new Error("Hit EOF early")

      (x4 << 24) + (x3 << 16) + (x2 << 8) + (x1 << 0)
    }

    val magic = new Array[Byte](MAGIC.length)

    for (i <- 0 until MAGIC.length) {
      magic(i) = array(footerLengthIndex + 4 + i)
    }

    array.pos = footerLengthIndex - footerLength

    magic.sameElements(MAGIC)
  }
}
