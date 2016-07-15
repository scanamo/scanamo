package com.gu

import com.gu.scanamo.query._
import com.gu.scanamo.update._

package object scanamo {

  object syntax extends Scannable.ToScannableOps with Queryable.ToQueryableOps {
    implicit class SymbolKeyCondition(s: Symbol) {
      def <[V: DynamoFormat](v: V) = KeyIs(s, LT, v)
      def >[V: DynamoFormat](v: V) = KeyIs(s, GT, v)
      def <=[V: DynamoFormat](v: V) = KeyIs(s, LTE, v)
      def >=[V: DynamoFormat](v: V) = KeyIs(s, GTE, v)
      def beginsWith[V: DynamoFormat](v: V) = BeginsWith(s, v)

      def and(other: Symbol) =  HashAndRangeKeyNames(s, other)
    }

    case class HashAndRangeKeyNames(hash: Symbol, range: Symbol)

    implicit def symbolTupleToUniqueKey[V: DynamoFormat](pair: (Symbol, V)) =
      UniqueKey(KeyEquals(pair._1, pair._2))

    implicit def symbolTupleToKeyCondition[V: DynamoFormat](pair: (Symbol, V)) =
      KeyEquals(pair._1, pair._2)

    implicit def toUniqueKey[T: UniqueKeyCondition](t: T) = UniqueKey(t)

    implicit def symbolListTupleToUniqueKeys[V: DynamoFormat](pair: (Symbol, Set[V])) =
      UniqueKeys(KeyList(pair._1, pair._2))

    implicit def toMultipleKeyList[H: DynamoFormat, R: DynamoFormat](pair: (HashAndRangeKeyNames, Set[(H, R)])) =
      UniqueKeys(MultipleKeyList(pair._1.hash -> pair._1.range, pair._2))

    implicit def symbolTupleToQuery[V: DynamoFormat](pair: (Symbol, V)) =
      Query(KeyEquals(pair._1, pair._2))

    implicit def toQuery[T: QueryableKeyCondition](t: T) = Query(t)

    def attributeExists(symbol: Symbol) = AttributeExists(symbol)

    def not[T: ConditionExpression](t: T) = Not(t)

    implicit class AndConditionExpression[X: ConditionExpression](x: X) {
      def and[Y: ConditionExpression](y: Y) = AndCondition(x, y)
    }

    implicit class OrConditionExpression[X: ConditionExpression](x: X) {
      def or[Y: ConditionExpression](y: Y) = OrCondition(x, y)
    }

    def set[V: DynamoFormat](fieldValue: (Symbol, V)) =
      SetExpression(fieldValue._1, fieldValue._2)
    def append[V: DynamoFormat](fieldValue: (Symbol, V)) =
      AppendExpression(fieldValue._1, fieldValue._2)
    def prepend[V: DynamoFormat](fieldValue: (Symbol, V)) =
      PrependExpression(fieldValue._1, fieldValue._2)
    def add[V: DynamoFormat](fieldValue: (Symbol, V)) =
      AddExpression(fieldValue._1, fieldValue._2)
    def delete[V: DynamoFormat](fieldValue: (Symbol, V)) =
      DeleteExpression(fieldValue._1, fieldValue._2)

    implicit class AndUpdateExpression[X: UpdateExpression](x: X) {
      def and[Y: UpdateExpression](y: Y) = AndUpdate(x, y)
    }
  }
}
