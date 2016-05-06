package com.gu

import com.gu.scanamo.query._

package object scanamo {

  object syntax extends Scannable.ToScannableOps with Queryable.ToQueryableOps {
    implicit class SymbolKeyCondition(s: Symbol) {
      def <[V: DynamoFormat](v: V) = KeyIs(s, LT, v)
      def >[V: DynamoFormat](v: V) = KeyIs(s, GT, v)
      def <=[V: DynamoFormat](v: V) = KeyIs(s, LTE, v)
      def >=[V: DynamoFormat](v: V) = KeyIs(s, GTE, v)
      def beginsWith[V: DynamoFormat](v: V) = KeyBeginsWith(s, v)

      def and(other: Symbol) =  HashAndRangeKeyNames(s, other)
    }

    case class HashAndRangeKeyNames(hash: Symbol, range: Symbol)

    implicit def symbolTupleToUniqueKey[V: DynamoFormat](pair: (Symbol, V)) =
      UniqueKey(KeyEquals(pair._1, pair._2))

    implicit def symbolTupleToKeyCondition[V: DynamoFormat](pair: (Symbol, V)) =
      KeyEquals(pair._1, pair._2)

    implicit def symbolTupleToConditionExpression[V: DynamoFormat](pair: (Symbol, V)) =
      ConditionExpression(KeyEquals(pair._1, pair._2))

    implicit def toUniqueKey[T: UniqueKeyCondition](t: T) = UniqueKey(t)

    implicit def symbolListTupleToUniqueKeys[V: DynamoFormat](pair: (Symbol, List[V])) =
      UniqueKeys(KeyList(pair._1, pair._2))

    implicit def toMultipleKeyList[H: DynamoFormat, R: DynamoFormat](pair: (HashAndRangeKeyNames, List[(H, R)])) =
      UniqueKeys(MultipleKeyList(pair._1.hash -> pair._1.range, pair._2))

    implicit def symbolTupleToQuery[V: DynamoFormat](pair: (Symbol, V)) =
      Query(KeyEquals(pair._1, pair._2))

    implicit def toQuery[T: QueryableKeyCondition](t: T) = Query(t)

    implicit def toConditionExpression[T: PutConditionState](t: T) = ConditionExpression(t)

    def attributeExists(symbol: Symbol) = AttributeExists(symbol)

    def not[T: PutConditionState](t: T) = Not(t)
  }
}
