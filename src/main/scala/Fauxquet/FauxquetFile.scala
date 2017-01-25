package main.scala.Fauxquet

import java.io.PrintWriter

/**
  * Created by james on 8/5/16.
  */
class FauxquetFile(val file: String) {
  type Schema = Vector[String]
  type Fields = Vector[String]

  case class Record(fields: Fields, schema: Schema) {
    def apply(key: String): String = fields(schema indexOf key)
    def apply(keys: Schema): Fields = keys.map(this apply _)
  }

  def Schema(schema: List[String]) = schema.toVector

  //val fauxquetReader = new FauxquetReader(file)

  var data: Map[String, Vector[Any]] = Map[String, Vector[Any]]() //need to change this when dealing with Record
  var schema: Schema = _
  var fields: Fields = _

  def read() = {
    val fauxquetReader = new FauxquetReader(file)
    data = fauxquetReader.read()
  }

  def write() = {
    val fauxquetWriter = new FauxquetWriter(file)
    fauxquetWriter.writeToCSV(data) //fauxquetWriter.write(data)
  }
}
