package main.scala

import java.io.{File, PrintWriter}
import java.util

import main.scala.Loader.{RInt, RString}
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.Path
import org.apache.parquet.column.ColumnDescriptor
import org.apache.parquet.column.impl.ColumnReadStoreImpl
import org.apache.parquet.column.page.{Page, PageReadStore}
import org.apache.parquet.hadoop.ParquetFileReader
import org.apache.parquet.hadoop.metadata.{ColumnChunkMetaData, ParquetMetadata}
import org.apache.parquet.io.api._
import org.apache.parquet.schema._

import scala.collection.JavaConversions._

/**
  * Created by jdecker on 6/21/16.
  */
object ParquetReader2 {
  type Fields = Vector[RField]
  type Schema = Vector[String]

  abstract class RField {
    def print()

    def compare(o: RField): Boolean

    def hash: Long
  }

  case class RString(data: String, len: Int) extends RField {
    def print() = println(data)

    def compare(o: RField) = o match {
      case RString(data2, len2) => if (len != len2) false
      else {
        // TODO: we may or may not want to inline this (code bloat and icache considerations).
        var i = 0
        while (i < len && data.charAt(i) == data2.charAt(i)) {
          i += 1
        }
        i == len
      }
    }

    def hash = data.hashCode()
  }

  case class RInt(value: Int) extends RField {
    def print() = printf("%d", value)

    def compare(o: RField) = o match {
      case RInt(v2) => value == v2
    }

    def hash = value.asInstanceOf[Long]
  }

  case class Record(fields: Fields, schema: Schema) {
    def apply(key: String): RField = fields(schema indexOf key)
    def apply(keys: Schema): Fields = keys.map(this apply _)
  }

  def Schema(schema: String*): Schema = schema.toVector

  def main(args: Array[String]) {
    val p = new Path("./resources/customer.parquet")
    val parquetMetadata = ParquetFileReader.readFooter(new Configuration(), p)
    val schema = parquetMetadata.getFileMetaData.getSchema

    val out = new PrintWriter(new File("./resources/customer_test2.txt"))
    dump(parquetMetadata, schema, p, out)
  }

  def dump(meta: ParquetMetadata, schema: MessageType, path: Path, out: PrintWriter = new PrintWriter(System.out)) = {
    val configuration = new Configuration()
    val blocks = meta.getBlocks
    val columns = schema.getColumns
    val rule = "-----------------------------------"

    def dumpMetaData(): Unit = {
      out.println(s"in dumpMetaData, about to loop i <- 0 until blocks.size() { blocks.size() = ${blocks.size()} }")
      for (i <- 0 until blocks.size()) {
        out.println(s"i = $i")

        if (i != 0) out.println()
        out.println(s"Row Group $i\n")
        out.println(rule)

        val ccmds = blocks.get(i).getColumns

        out.println("starting printStuff(ccmds)")
        printStuff(ccmds)
        out.println("finished printStuff(ccmds)")

        out.println(s"creating ParquetFileReader for i = $i")
        val freader = new ParquetFileReader(configuration, path, blocks, columns)

        out.println("store = freader.readNextRowGroup()")
        var store = freader.readNextRowGroup()
        while (store != null) {
          out.println("store was not null")

          for (i <- 0 until columns.size()) {
            out.println("about to dump the metadata (inner call to dump(store, columns.get(i)))")
            dump(store, columns.get(i))
          }

          store = freader.readNextRowGroup()
        }

        out.println("closing freader")
        freader.close()
      }

      out.println("exiting dumpMetaData")
    }

    def dumpData(): Unit = {
      def dump(crstore: ColumnReadStoreImpl, column: ColumnDescriptor, page: Long, total: Long, offset: Long) = {
        val dmax = column.getMaxDefinitionLevel
        val creader = crstore.getColumnReader(column)
        out.print(s"*** row group $page of $total, values $offset to ${offset + creader.getTotalValueCount - 1}")
        out.println()

        for (i <- 0L until creader.getTotalValueCount) {
          val rlvl = creader.getCurrentDefinitionLevel
          val dlvl = creader.getCurrentDefinitionLevel

          out.print(s"value ${offset + i}: R:$rlvl D:$dlvl V:")

          if (dlvl == dmax) {
            def BtoS() = out.print(new String(creader.getBinary.getBytes))

            column.getType match {
              case PrimitiveType.PrimitiveTypeName.BINARY => BtoS()
              case PrimitiveType.PrimitiveTypeName.BOOLEAN => out.print(creader.getBoolean)
              case PrimitiveType.PrimitiveTypeName.DOUBLE => out.print(creader.getDouble)
              case PrimitiveType.PrimitiveTypeName.FLOAT => out.print(creader.getFloat)
              case PrimitiveType.PrimitiveTypeName.INT32 => out.print(creader.getInteger)
              case PrimitiveType.PrimitiveTypeName.INT64 => out.print(creader.getLong)
              case PrimitiveType.PrimitiveTypeName.INT96 => BtoS()
              case PrimitiveType.PrimitiveTypeName.FIXED_LEN_BYTE_ARRAY => BtoS()
            }
          } else {
            out.print("<null>")
          }

          out.println()
          creader.consume()
        }
      }

      for (i <- 0 until columns.size()) {
        val column = columns.get(i)
        out.println()

        out.println(s"${column.getType} ${column.getPath.mkString(".")}")
        out.println(rule)

        var page = 1L
        val total = blocks.size()
        var offset = 1L

        val freader = new ParquetFileReader(configuration, path, blocks, columns)
        var store = freader.readNextRowGroup()

        while (store != null) {
          val crstore = new ColumnReadStoreImpl(store, new DumpGroupConverter(), schema, "James Decker")
          dump(crstore, column, page, total, offset)

          page += 1
          offset += store.getRowCount
          store = freader.readNextRowGroup()
        }

        freader.close()
      }

      class DumpGroupConverter extends GroupConverter {
        override def getConverter(i: Int): Converter = { new DumpConverter() }

        override def end(): Unit = {}

        override def start(): Unit = {}
      }

      class DumpConverter extends PrimitiveConverter {
        override def isPrimitive = true
        override def asGroupConverter() = new DumpGroupConverter()
      }
    }

    def dump(store: PageReadStore, column: ColumnDescriptor): Unit = {
      val reader = store.getPageReader(column)
      val rule = "-----------------------------------"

      val vc = reader.getTotalValueCount
      val rmax = column.getMaxRepetitionLevel
      val dmax = column.getMaxDefinitionLevel
      out.print(s"${column.getPath.mkString(".")} TV=$vc RL=$rmax DL=$dmax")

      val dict = reader.readDictionaryPage()

      if (dict != null) {
        out.print(s" DS:${dict.getDictionarySize} DE:${dict.getEncoding}")
      }

      out.println()
      out.println(rule)

      val page: Page = reader.readPage()

      var i = 0

      while (page != null) {
        out.print(s"page $i: DLE:$page")
        i += 1
      }
    }

    def printStuff(ccmds: util.List[ColumnChunkMetaData]): Unit = {
      val chunks: util.Map[String, Object] = new util.LinkedHashMap[String, Object]()

      out.println(s"starting i <- 0 until ccmds.size() where ccmds.size() = ${ccmds.size()}")
      for (i <- 0 until ccmds.size()) {
        out.println()
        out.println(s"i = $i")

        val cmeta = ccmds.get(i)
        val path = cmeta.getPath.toArray

        var current = chunks

        out.println("about to loop over path.length")
        for (j <- 0 until path.length) {
          out.println(s"j = $j")

          val next = path(j)

          if (!current.containsKey(next)) {
            current.put(next, new util.LinkedHashMap[String, Object]())
          }

          current = current.get(next).asInstanceOf[util.LinkedHashMap[String, Object]]
        }

        current.put(path(path.length - 1), cmeta)
      }

      out.println("calling showColumnChunkDetails(chunks, 0)")
      showColumnChunkDetails(chunks, 0)
      out.println("made it out of showColumnChunkDetails")

      out.println("exiting printStuff")
    }

    def showColumnChunkDetails(current: util.Map[String, Object], depth: Int): Unit = {


      for (entry: util.Map.Entry[String, Object] <- current.entrySet()) {
        val name = entry.getKey
        val value: Object = entry.getValue

        if (value.isInstanceOf[util.Map[String, Object]]) {
          out.println(name + ": ")
          showColumnChunkDetails(value.asInstanceOf[util.Map[String, Object]], depth + 1)
        } else {
          out.print(name + ": ")
          showDetails(value.asInstanceOf[ColumnChunkMetaData], name = false)
        }
      }
    }

    def showDetails(meta: ColumnChunkMetaData, name: Boolean) = {
      val doff = meta.getDictionaryPageOffset
      val foff = meta.getFirstDataPageOffset
      val tsize = meta.getTotalSize
      val usize = meta.getTotalUncompressedSize
      val count = meta.getValueCount
      val ratio = usize / tsize.asInstanceOf[Double]
      val encodings = meta.getEncodings.toArray.mkString(",")

      if (name) {
        out.print(meta.getPath.toArray.mkString(",") + ": ")
      }

      out.print(s" ${meta.getType} ${meta.getCodec} DO:$doff FPO:$foff SZ:$tsize/$usize/$ratio VC:$count")
      if (!encodings.isEmpty) out.print(s" ENC:$encodings")
      out.println()
      out.println()
    }

    //dumpMetaData()
    dumpData()
  }
}