package org.scanamo

import org.scalatest.{ FunSpecLike, Matchers }

class DynamoFormatSpec extends FunSpecLike with Matchers {
  sealed abstract class StringList
  case object Empty extends StringList
  final case class Node(head: String, tail: StringList) extends StringList

  it("should accept a custom default when using iso") {
    val df =
      DynamoFormat.iso[StringList, List[String]](_ => Empty, DynamoDefault.SomeDef(Node("Hello", Empty)))(_ => Nil)

    df.default should be(Some(Node("Hello", Empty)))
  }

  it("should accept a custom default when using xmap") {
    val df =
      DynamoFormat.xmap[StringList, List[String]](_ => Right(Empty), DynamoDefault.SomeDef(Node("Hello", Empty)))(
        _ => Nil
      )

    df.default should be(Some(Node("Hello", Empty)))
  }

  it("should accept a custom default when using coercedXmap") {
    val df =
      DynamoFormat.coercedXmap[StringList, List[String], Throwable](
        _ => Empty,
        DynamoDefault.SomeDef(Node("Hello", Empty))
      )(
        _ => Nil
      )

    df.default should be(Some(Node("Hello", Empty)))
  }

  it("should accept no default when using iso") {
    val df = DynamoFormat.iso[StringList, List[String]](_ => Empty, DynamoDefault.NoDef)(_ => Nil)

    df.default should be(None)
  }

  it("should accept no default when using xmap") {
    val df = DynamoFormat.xmap[StringList, List[String]](_ => Right(Empty), DynamoDefault.NoDef)(_ => Nil)

    df.default should be(None)
  }

  it("should accept no default when using coercedXmap") {
    val df =
      DynamoFormat.coercedXmap[StringList, List[String], Throwable](_ => Empty, DynamoDefault.NoDef)(
        _ => Nil
      )

    df.default should be(None)
  }

  it("should map the default when using iso") {
    val df = DynamoFormat.iso[StringList, String](_ => Empty, DynamoDefault.MapDef)(_ => "Hello")

    df.default should be(Some(Empty))
  }

  it("should map the default when using xmap") {
    val df = DynamoFormat.xmap[StringList, String](_ => Right(Empty), DynamoDefault.MapDef)(_ => "Hello")

    df.default should be(Some(Empty))
  }

  it("should map the default when using coercedXmap") {
    val df =
      DynamoFormat.coercedXmap[StringList, String, Throwable](_ => Empty, DynamoDefault.MapDef)(
        _ => "Hello"
      )

    df.default should be(Some(Empty))
  }
}
