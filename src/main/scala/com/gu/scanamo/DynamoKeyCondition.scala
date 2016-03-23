package com.gu.scanamo

import com.amazonaws.services.dynamodbv2.model.{AttributeValue, QueryRequest}
import simulacrum.typeclass

import collection.convert.decorateAsJava._

sealed abstract class DynamoOperator(val op: String)
object LT  extends DynamoOperator("<")
object LTE extends DynamoOperator("<=")
object GT  extends DynamoOperator(">")
object GTE extends DynamoOperator(">=")


@typeclass trait QueryableKeyCondition[T] {
  def apply(t: T)(req: QueryRequest): QueryRequest
}

case class Query[T](t: T)(implicit qkc: QueryableKeyCondition[T]) {
  def apply(req: QueryRequest): QueryRequest =
    qkc.apply(t)(req)
}

object QueryableKeyCondition {
  implicit def equalsKeyCondition[V] = new QueryableKeyCondition[KeyEquals[V]] {
    override def apply(t: KeyEquals[V])(req: QueryRequest): QueryRequest =
      req.withKeyConditionExpression(s"#K = :${t.key.name}")
        .withExpressionAttributeNames(Map("#K" -> t.key.name).asJava)
        .withExpressionAttributeValues(Map(s":${t.key.name}" -> t.f.write(t.v)).asJava)
  }
  implicit def hashAndRangeQueryCondition[H, R] = new QueryableKeyCondition[AndQueryCondition[H, R]] {
    override def apply(t: AndQueryCondition[H, R])(req: QueryRequest): QueryRequest =
      req
        .withKeyConditionExpression(
          s"#K = :${t.hashCondition.key.name} AND ${t.rangeCondition.keyConditionExpression("R")}"
        )
        .withExpressionAttributeNames(Map("#K" -> t.hashCondition.key.name, "#R" -> t.rangeCondition.key.name).asJava)
        .withExpressionAttributeValues(
          Map(
            s":${t.hashCondition.key.name}" -> t.fH.write(t.hashCondition.v),
            s":${t.rangeCondition.key.name}" -> t.fR.write(t.rangeCondition.v)
          ).asJava
        )
  }
}

case class UniqueKey[T](t: T)(implicit val ukc: UniqueKeyCondition[T]) {
  def asAVMap: Map[String, AttributeValue] = ukc.asAVMap(t)
}

@typeclass trait UniqueKeyCondition[T] {
  def asAVMap(t: T): Map[String, AttributeValue]
}
object UniqueKeyCondition {
  implicit def uniqueEqualsKey[V] = new UniqueKeyCondition[KeyEquals[V]] {
    override def asAVMap(t: KeyEquals[V]): Map[String, AttributeValue] =
      Map(t.key.name -> t.f.write(t.v))
  }
  implicit def uniqueAndEqualsKey[H, R] = new UniqueKeyCondition[AndEqualsCondition[H, R]] {
    override def asAVMap(t: AndEqualsCondition[H, R]): Map[String, AttributeValue] =
      t.hashCondition.asAVMap(t.hashEquality) ++ t.rangeCondition.asAVMap(t.rangeEquality)
  }
}

case class AndEqualsCondition[H, R](hashEquality: H, rangeEquality: R)(
  implicit val hashCondition: UniqueKeyCondition[H], val rangeCondition: UniqueKeyCondition[R])

case class KeyEquals[V](key: Symbol, v: V)(implicit val f: DynamoFormat[V]) {
  def and[R](rangeKeyCondition: RangeKeyCondition[R])(implicit fR: DynamoFormat[R]) =
    AndQueryCondition(this, rangeKeyCondition)
  def and[R](equalsKeyCondition: KeyEquals[R])(implicit fR: DynamoFormat[R]) =
    AndEqualsCondition(this, equalsKeyCondition)
}
case class AndQueryCondition[H, R](hashCondition: KeyEquals[H], rangeCondition: RangeKeyCondition[R])
  (implicit val fH: DynamoFormat[H], val fR: DynamoFormat[R])

sealed abstract class RangeKeyCondition[V](implicit f: DynamoFormat[V]) {
  val key: Symbol
  val v: V
  def keyConditionExpression(s: String): String
}

case class KeyIs[V](key: Symbol, operator: DynamoOperator, v: V)(implicit f: DynamoFormat[V]) extends RangeKeyCondition[V]{
  def keyConditionExpression(s: String): String = s"#$s ${operator.op} :${key.name}"
}

case class KeyBeginsWith[V](key: Symbol, v: V)(implicit f: DynamoFormat[V]) extends RangeKeyCondition[V] {
  override def keyConditionExpression(s: String): String = s"begins_with(#$s, :${key.name})"
}
