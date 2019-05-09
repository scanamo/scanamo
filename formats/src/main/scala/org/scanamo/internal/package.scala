package org.scanamo

import java.util.{function => juf}

package object internal {
  import com.amazonaws.services.dynamodbv2.model.AttributeValue
  import java.nio.ByteBuffer
  private[scanamo] def jfun[A, B](f: A => B): juf.Function[A, B] = new juf.Function[A, B] {
    def apply(x: A) = f(x)
  }

  private[scanamo] def jcon[A](f: A => Unit): juf.Consumer[A] = new juf.Consumer[A] {
    def accept(x: A) = f(x)
  }

  private[scanamo] def jbiop[A](f: (A, A) => A): juf.BinaryOperator[A] = new juf.BinaryOperator[A] {
    def apply(x: A, y: A) = f(x, y)
  }

  private[scanamo] def jcon2[A, B](f: (A, B) => Unit): juf.BiConsumer[A, B] = new juf.BiConsumer[A, B] {
    def accept(x: A, y: B) = f(x, y)
  }

  private[scanamo] def jfun2[A, B, C](f: (A, B) => C): juf.BiFunction[A, B, C] = new juf.BiFunction[A, B, C] {
    def apply(x: A, y: B) = f(x, y)
  }

  private[scanamo] val concatStrings = jbiop[List[String]](_ ++ _)
  private[scanamo] val concatByteBuffers = jbiop[List[ByteBuffer]](_ ++ _)
  private[scanamo] val concatDynamoValues = jbiop[List[DynamoValue]](_ ++ _)
  private[scanamo] val concatDynamoObjects = jbiop[List[DynamoObject]](_ ++ _)
  private[scanamo] val prependString = jfun2[List[String], String, List[String]](_ :+ _)
  private[scanamo] val prependByteBuffer = jfun2[List[ByteBuffer], ByteBuffer, List[ByteBuffer]](_ :+ _)
  private[scanamo] val prependDynamoValue = jfun2[List[DynamoValue], DynamoValue, List[DynamoValue]](_ :+ _)
  private[scanamo] val prependDynamoObject =
    jfun2[List[DynamoObject], java.util.Map[String, AttributeValue], List[DynamoObject]](
      (xs, x) => xs :+ DynamoObject(x)
    )
}
