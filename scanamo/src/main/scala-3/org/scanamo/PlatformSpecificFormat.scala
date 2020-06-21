package org.scanamo

import cats.instances.either._
import cats.instances.list._
import cats.syntax.parallel._
import cats.syntax.traverse._

import scala.compiletime.{ S, constValue, erasedValue, summonInline }
import scala.deriving._

object Derivation {
  inline final def length[T <: Tuple]: Int = 
    inline erasedValue[T] match {
      case _: Unit => 0
      case _: (_ *: ts) => 1 + length[ts]
    }
  
  inline final def summonAll[T <: Tuple]: List[DynamoFormat[_]] = 
    inline erasedValue[T] match {
      case _: Unit => Nil
      case _: (t *: ts) => summonInline[DynamoFormat[t]] :: summonAll[ts]
    }

  inline final def summonAllLabels[T <: Tuple]: List[String] =
    inline erasedValue[T] match {
      case _: Unit => Nil
      case _: (t *: ts) => constValue[t].asInstanceOf[String] :: summonAllLabels[ts]
    }
}

trait PlatformSpecificFormat {
  inline given derived[A](using m: Mirror.Of[A]) as DynamoFormat.ObjectFormat[A] =
    inline m match {
      case s: Mirror.SumOf[A] =>
        new DerivedSumFormat[A](
          s,
          Derivation.summonAll[s.MirroredElemTypes].toArray, 
          Derivation.summonAllLabels[s.MirroredElemLabels].toArray
        )

      case p: Mirror.ProductOf[A] => 
        new DerivedProductFormat[A](
          p,
          Derivation.summonAll[p.MirroredElemTypes].toArray,
          Derivation.summonAllLabels[p.MirroredElemLabels].toArray,
          Derivation.length[m.MirroredElemTypes]
        )
    }
}

private[scanamo] final class DerivedSumFormat[A](
  s: Mirror.SumOf[A],
  instances: Array[DynamoFormat[_]],
  labels: Array[String]
) extends DynamoFormat.ObjectFormat[A] {
  lazy val labelsAndInstances: Array[(String, DynamoFormat[_])] = labels zip instances

  def tryRead(o: DynamoObject): Option[DynamoFormat.Result[A]] =
    labelsAndInstances.collectFirst { 
      case (k, f) if o.contains(k) => o(k).get.as(f).asInstanceOf[DynamoFormat.Result[A]] 
    }

  def write(x: A, idx: Int): DynamoObject =
    DynamoObject.singleton(labels(idx), instances(idx).asInstanceOf[DynamoFormat[A]].write(x))

  def readObject(o: DynamoObject): DynamoFormat.Result[A] =
    tryRead(o).getOrElse(Left(MissingProperty))

  def writeObject(x: A): DynamoObject =
    write(x, s.ordinal(x))
}

private[scanamo] final class DerivedProductFormat[A](
  m: Mirror.ProductOf[A],
  instances: Array[DynamoFormat[_]],
  labels: Array[String],
  elemsLength: Int
) extends DynamoFormat.ObjectFormat[A] {
  lazy val labelsAndInstances: Array[(String, DynamoFormat[_])] = labels zip instances

  def tryRead(o: DynamoObject): DynamoFormat.Result[Product] = {
    val elems: Array[AnyRef] = Array.ofDim[AnyRef](elemsLength)
    var error: Left[DynamoReadError, Product] = null
    var i = 0
    while(i < elemsLength) {
      o.get(labels(i))(instances(i)) match {
        case x: Left[_, _] => 
          error = x.asInstanceOf[Left[DynamoReadError, Product]]
          i = elemsLength
        case Right(x) =>
          elems(i) = x.asInstanceOf[AnyRef]
          i += 1
      }
    }

    if (error eq null)
      Right(new ArrayProduct(elems))
    else
      error
  }

  def write(p: Product): Iterable[(String, DynamoValue)] =
    new Iterable[(String, DynamoValue)] {
      val iterator: Iterator[(String, DynamoValue)] =
        new Iterator[(String, DynamoValue)] {
          var i = 0
          val it = p.productIterator

          def hasNext: Boolean = it.hasNext && i < elemsLength
          
          def next(): (String, DynamoValue) = {
            val v = instances(i).asInstanceOf[DynamoFormat[Any]].write(it.next)
            val l = labels(i)
            i += 1
            l -> v
          }
        }
    }

  def readObject(o: DynamoObject): DynamoFormat.Result[A] =
    tryRead(o).map(m.fromProduct)

  def writeObject(x: A): DynamoObject =
    DynamoObject.fromIterable(write(x.asInstanceOf[Product]))
}