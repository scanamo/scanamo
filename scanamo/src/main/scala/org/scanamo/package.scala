package org

import org.scanamo.query._
import org.scanamo.update._

package object scanamo {

  object syntax {
    implicit class StringKeyCondition(s: String) {
      def and(other: String) = HashAndRangeKeyNames(s, other)
    }

    case class HashAndRangeKeyNames(hash: String, range: String)

    implicit def stringTupleToUniqueKey[V: DynamoFormat](pair: (String, V)) =
      UniqueKey(KeyEquals(pair._1, pair._2))

    implicit def stringTupleToKeyCondition[V: DynamoFormat](pair: (String, V)) =
      KeyEquals(pair._1, pair._2)

    implicit def toUniqueKey[T: UniqueKeyCondition](t: T) = UniqueKey(t)

    implicit def stringListTupleToUniqueKeys[V: DynamoFormat](pair: (String, Set[V])) =
      UniqueKeys(KeyList(pair._1, pair._2))

    implicit def toMultipleKeyList[H: DynamoFormat, R: DynamoFormat](pair: (HashAndRangeKeyNames, Set[(H, R)])) =
      UniqueKeys(MultipleKeyList(pair._1.hash -> pair._1.range, pair._2))

    implicit def stringTupleToQuery[V: DynamoFormat](pair: (String, V)) =
      Query(KeyEquals(pair._1, pair._2))

    implicit def toQuery[T: QueryableKeyCondition](t: T) = Query(t)

    case class Bounds[V: DynamoFormat](lowerBound: Bound[V], upperBound: Bound[V])

    implicit class Bound[V: DynamoFormat](val v: V) {
      def and(upperBound: V) = Bounds(Bound(v), Bound(upperBound))
    }

    def attributeExists(string: String) = AttributeExists(AttributeName.of(string))

    def attributeNotExists(string: String) = AttributeNotExists(AttributeName.of(string))

    def not[T: ConditionExpression](t: T) = Not(t)

    implicit class AndConditionExpression[X: ConditionExpression](x: X) {
      def and[Y: ConditionExpression](y: Y) = AndCondition(x, y)
    }

    implicit class OrConditionExpression[X: ConditionExpression](x: X) {
      def or[Y: ConditionExpression](y: Y) = OrCondition(x, y)
    }

    def set(fields: (AttributeName, AttributeName)): UpdateExpression =
      UpdateExpression.setFromAttribute(fields)
    def set[V: DynamoFormat](fieldValue: (AttributeName, V)): UpdateExpression =
      UpdateExpression.set(fieldValue)
    def append[V: DynamoFormat](fieldValue: (AttributeName, V)): UpdateExpression =
      UpdateExpression.append(fieldValue)
    def prepend[V: DynamoFormat](fieldValue: (AttributeName, V)): UpdateExpression =
      UpdateExpression.prepend(fieldValue)
    def appendAll[V: DynamoFormat](fieldValue: (AttributeName, List[V])): UpdateExpression =
      UpdateExpression.appendAll(fieldValue)
    def prependAll[V: DynamoFormat](fieldValue: (AttributeName, List[V])): UpdateExpression =
      UpdateExpression.prependAll(fieldValue)
    def add[V: DynamoFormat](fieldValue: (AttributeName, V)): UpdateExpression =
      UpdateExpression.add(fieldValue)
    def delete[V: DynamoFormat](fieldValue: (AttributeName, V)): UpdateExpression =
      UpdateExpression.delete(fieldValue)
    def remove(field: AttributeName): UpdateExpression =
      UpdateExpression.remove(field)

    implicit def stringAttributeName(s: String): AttributeName = AttributeName.of(s)
    implicit def stringAttributeNameValue[T](sv: (String, T)): (AttributeName, T) = AttributeName.of(sv._1) -> sv._2
    implicit def stringTupleAttributeNameTuple(ss: (String, String)): (AttributeName, AttributeName) =
      AttributeName.of(ss._1) -> AttributeName.of(ss._2)

    implicit class AndUpdateExpression(x: UpdateExpression) {
      def and(y: UpdateExpression): UpdateExpression = AndUpdate(x, y)
    }
  }
}
