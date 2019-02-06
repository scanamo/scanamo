package org.scanamo.aws
import java.nio.ByteBuffer

import com.amazonaws.services.dynamodbv2.model.{AttributeValue => v1AttributeValue}
import simulacrum.typeclass
//import software.amazon.awssdk.services.dynamodb.model

import scala.collection.JavaConverters._

object models {
  // TODO: rename to dynamoDB
  @typeclass trait AmazonAttribute[A] {

    type Func[T] = A => T
    type FuncBack[T] = A => T => A

    def getString: Func[String]
    def getNumericString: Func[String]
    def getBoolean: Func[Boolean]
    def getBytesArray: Func[Array[Byte]]
    def getBytesBuffer: Func[ByteBuffer]

    def isNull: Func[Boolean]
    def getNS: Func[List[String]]
    def getSS: Func[List[String]]
    def getList: Func[List[A]]
    def getMap: Func[Map[String, A]]

    def setString: FuncBack[String]
    def setNumericString: FuncBack[String]
    def setBoolean: FuncBack[Boolean]
    def setBytesArray: FuncBack[Array[Byte]]
    def setBytesBuffer: FuncBack[ByteBuffer]

    def setNull: A => A
    def setNS: FuncBack[List[String]]
    def setSS: FuncBack[List[String]]
    def setList: FuncBack[List[A]]
    def setMap: FuncBack[Map[String, A]]

    def init: A
  }

  object AmazonAttribute {
    implicit val v1Aws: AmazonAttribute[v1AttributeValue] = new AmazonAttribute[v1AttributeValue] {
      override val getString: Func[String] = _.getS
      override val getNumericString: Func[String] = _.getN
      override val getBoolean: Func[Boolean] = _.getBOOL
      override val getBytesArray: Func[Array[Byte]] = a => a.getB.array()
      override def getBytesBuffer: Func[ByteBuffer] = a => a.getB
      override def isNull: Func[Boolean] = { av =>
        (av.isNULL ne null) && av.isNULL
      }

      override def getNS: Func[List[String]] = _.getNS.asScala.toList
      override def getSS: Func[List[String]] = _.getSS.asScala.toList
      override def getList: Func[List[v1AttributeValue]] = _.getL.asScala.toList
      override def getMap: Func[Map[String, v1AttributeValue]] = _.getM.asScala.toMap

      override def init: v1AttributeValue = new v1AttributeValue()

      override def setString: FuncBack[String] = _.withS
      override def setNumericString: FuncBack[String] = _.withN
      override def setBoolean: FuncBack[Boolean] =  a => {
        val f: Boolean => java.lang.Boolean = Boolean2boolean(_)
        f.andThen(a.withBOOL)
      }
      override def setBytesArray: FuncBack[Array[Byte]] = b => {
        val f: Array[Byte] => ByteBuffer = ByteBuffer.wrap
        f.andThen(b.withB)
      }
      override def setBytesBuffer: FuncBack[ByteBuffer] = _.withB
      override def setNull: v1AttributeValue => v1AttributeValue = a => {
        a.withNULL(true)
      }

      override def setNS: FuncBack[List[String]] = a => {
        val f: List[String] => java.util.Collection[String] = _.asJava
        f.andThen(a.withNS)
      }
      override def setSS: FuncBack[List[String]] = a => {
        val f: List[String] => java.util.Collection[String] = _.asJava
        f.andThen(a.withSS)
      }

      override def setList: FuncBack[List[v1AttributeValue]] = a => {
        val f: List[v1AttributeValue] => java.util.Collection[v1AttributeValue] = _.asJava
        f.andThen(a.withL)
      }
      override def setMap: FuncBack[Map[String, v1AttributeValue]] = a => {
        val f: Map[String, v1AttributeValue] => java.util.Map[String, v1AttributeValue] = _.asJava
        f.andThen(a.withM)
      }
    }
  }
}
/**
  *
  *  override val getNumericString: Func[String] = _.n
  *       override val getBoolean: Func[Boolean] = _.bool
  *       override val getBytesArray: Func[Array[Byte]] = a => a.b().asByteArray()
  *       override def getBytesBuffer: Func[ByteBuffer] = a => a.b().asByteBuffer()
  *       override def isNull: Func[Boolean] = a => Option(a.nul).contains(true)
  *       override def getNS: Func[List[String]] = _.ns().asScala.toList
  *       override def getSS: Func[List[String]] = _.ss().asScala.toList
  *       override def getList: Func[List[v1AttributeValue]] = _.l.asScala.toList
  *       override def getMap: Func[Map[String, v1AttributeValue]] = _.m.asScala.toMap
  *
  *       override def init: v1AttributeValue = v1AttributeValue.builder()
  *
  *
  * */
