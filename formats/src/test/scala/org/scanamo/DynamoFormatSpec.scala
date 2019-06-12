package org.scanamo

import org.scalatest.{ FunSpecLike, Matchers }

class DynamoFormatSpec extends FunSpecLike with Matchers {
  sealed abstract class StringList
  case object Empty extends StringList
  final case class Node(head: String, tail: StringList) extends StringList

  it("should accept a custom default when using iso") {
    val df = DynamoFormat.iso[StringList, List[String]](_ => Empty, Some(Some(Node("Hello", Empty))))(_ => Nil)

    df.default should be (Some(Node("Hello", Empty)))
  }
  
  it("should accept a custom default when using xmap") {
    val df = DynamoFormat.xmap[StringList, List[String]](_ => Right(Empty), Some(Some(Node("Hello", Empty))))(_ => Nil)

    df.default should be (Some(Node("Hello", Empty)))
  }
  
  it("should accept a custom default when using coercedXmap") {
    val df = DynamoFormat.coercedXmap[StringList, List[String], Throwable](_ => Empty, Some(Some(Node("Hello", Empty))))(_ => Nil)

    df.default should be (Some(Node("Hello", Empty)))
  }
}