package com.gu

package object scanamo {
  object syntax {
    implicit class SymbolKeyCondition(s: Symbol) {
      def <[V](v: V)(implicit f: DynamoFormat[V]) = KeyIs(s, LT, v)
      def >[V](v: V)(implicit f: DynamoFormat[V]) = KeyIs(s, GT, v)
      def <=[V](v: V)(implicit f: DynamoFormat[V]) = KeyIs(s, LTE, v)
      def >=[V](v: V)(implicit f: DynamoFormat[V]) = KeyIs(s, GTE, v)
      def beginsWith[V](v: V)(implicit f: DynamoFormat[V]) = KeyBeginsWith(s, v)


      def ===[V](v: V)(implicit f: DynamoFormat[V]) = KeyEquals(s, v)
    }


    implicit def symbolTupleToKeyCondition[V](pair: (Symbol, V))(implicit f: DynamoFormat[V]) =
      KeyEquals(pair._1, pair._2)

    implicit def toAVMap[T](t: T)(implicit kc: UniqueKeyCondition[T]) =
      UniqueKey(t)(kc)

    implicit def toQuery[T](t: T)(implicit queryableKeyCondition: QueryableKeyCondition[T]) =
      Query(t)(queryableKeyCondition)
  }
}
