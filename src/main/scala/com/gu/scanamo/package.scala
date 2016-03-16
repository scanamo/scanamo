package com.gu

import com.amazonaws.services.dynamodbv2.model.{AttributeValue, QueryRequest}

package object scanamo {
  object syntax {
    implicit class SymbolKeyCondition(s: Symbol) {
      def <[V](v: V)(implicit f: DynamoFormat[V]) = SimpleKeyCondition(s, v, LT)
      def >[V](v: V)(implicit f: DynamoFormat[V]) = SimpleKeyCondition(s, v, GT)
      def <=[V](v: V)(implicit f: DynamoFormat[V]) = SimpleKeyCondition(s, v, LTE)
      def >=[V](v: V)(implicit f: DynamoFormat[V]) = SimpleKeyCondition(s, v, GTE)
      def beginsWith[V](v: V)(implicit f: DynamoFormat[V]) = BeginsWithCondition(s, v)


      def ===[V](v: V)(implicit f: DynamoFormat[V]) = EqualsKeyCondition(s, v)
    }


    implicit def symbolTupleToKeyCondition[V](pair: (Symbol, V))(implicit f: DynamoFormat[V]) =
      EqualsKeyCondition(pair._1, pair._2)

    implicit def toAVMap[T](t: T)(implicit kc: UniqueKeyCondition[T]) =
      new AttributeValueMap {
        override def asAVMap: Map[String, AttributeValue] = kc.asAVMap(t)
      }

    implicit def toQuery[T](t: T)(implicit queryableKeyCondition: QueryableKeyCondition[T]) =
      new Query {
        override def apply(req: QueryRequest): QueryRequest = queryableKeyCondition(t)(req)
      }
  }
}
