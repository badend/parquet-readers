package main.scala.Fauxquet.bytes.BytesInput

import java.io.{ByteArrayOutputStream, InputStream, OutputStream}
import java.nio.ByteBuffer

import main.scala.Fauxquet.bytes.CapacityByteArrayOutputStream

/**
  * Created by james on 1/26/17.
  * Thought I could get away without this one...might need to refactor a shizload :(
  */
trait BytesInput {
  def toByteArray: Array[Byte] = {
    val b = new BAOS(size.asInstanceOf[Int])
    writeAllTo(b)
    b.getBuf
  }

  def toByteBuffer: ByteBuffer = {
    ByteBuffer.wrap(this.toByteArray)
  }

  def size: Long
  def writeAllTo(out: OutputStream): Unit
}

object BytesInput extends BytesInput {
  /**
    * @param bytesInput
    * @return SequenceBytesIn
    */
  def concat(bytesInput: BytesInput*): BytesInput = new SequenceBytesIn(bytesInput.toList)

  /**
    * @param bytesInputs
    * @return SequenceBytesIn
    */
  def concat(bytesInputs: List[BytesInput]) = new SequenceBytesIn(bytesInputs)

  /**
    * @param in
    * @param bytes
    * @return StreamBytesInput
    */
  def from(in: InputStream, bytes: Int): BytesInput = new StreamBytesInput(in, bytes)

  /**
    * @param byteBuffer
    * @param offset
    * @param length
    * @return ByteBufferBytesInput
    */
  def from(byteBuffer: ByteBuffer, offset: Int, length: Int): BytesInput = new ByteBufferBytesInput(byteBuffer, offset, length)

  //ByteArrayBytesInput
  def from(in: Array[Byte]): BytesInput = new ByteArrayBytesInput(in, 0, in.length)
  def from(in: Array[Byte], offset: Int, length: Int): BytesInput = new ByteArrayBytesInput(in, offset, length)

  //IntBytesInput
  def fromInt(int: Int): BytesInput = new IntBytesInput(int)

  //UnsignedVarIntBytesInput
  def fromUnsignedVarInt(int: Int): BytesInput = new UnsignedVarIntBytesInput(int)
  def fromZigZagInt(int: Int): BytesInput = fromUnsignedVarInt((int << 1) ^ (int >> 31))

  //UnsignedVarLongBytesInput
  def fromUnsignedVarLong(long: Long): BytesInput = new UnsignedVarLongBytesInput(long)
  def fromZigZagLong(long: Long): BytesInput = fromUnsignedVarLong((long << 1) & (long >> 63))

  //CapacityBaosBytesInput
  def from(capacityByteArrayOutputStream: CapacityByteArrayOutputStream): BytesInput = new CapacityBaosBytesInput(capacityByteArrayOutputStream)

  //BaosBytesInput
  def from(byteArrayOutputStream: ByteArrayOutputStream): BytesInput = new BaosBytesInput(byteArrayOutputStream)

  def empty(): BytesInput = EmptyBytesInput

  def copy(bytesInput: BytesInput): BytesInput = from(bytesInput.toByteArray)

  override def size: Long = ???

  override def writeAllTo(out: OutputStream): Unit = ???
}