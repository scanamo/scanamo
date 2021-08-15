package org.scanamo.generic

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scanamo._

class AutoDerivationTest extends AnyFunSuite with Matchers {

  test("Derivation should correctly encode Option with Map inside") {
    import org.scanamo.generic.auto._

    case class OptionWrapper(theOption: Option[InnerWrapper])
    case class InnerWrapper(innerMap: Map[String, String])

    val value = OptionWrapper(Some(InnerWrapper(Map())))

    val result = DynamoFormat[OptionWrapper].write(value)

    result should ===(
      DynamoValue.fromDynamoObject(
        DynamoObject(
          "theOption" -> DynamoValue.fromDynamoObject(
            DynamoObject("innerMap" -> DynamoValue.fromDynamoObject(DynamoObject.empty))
          )
        )
      )
    )
  }

  test("Derivation should correctly encode Option with List inside") {
    import org.scanamo.generic.auto._

    case class OptionWrapper(theOption: Option[InnerWrapper])
    case class InnerWrapper(innerList: List[String])

    val value = OptionWrapper(Some(InnerWrapper(List())))

    val result = DynamoFormat[OptionWrapper].write(value)

    result should ===(
      DynamoValue.fromDynamoObject(
        DynamoObject(
          "theOption" -> DynamoValue.fromDynamoObject(
            DynamoObject("innerList" -> DynamoValue.fromDynamoArray(DynamoArray(List())))
          )
        )
      )
    )
  }

  test("Derivation should prioritise implicits from DynamoFormat companion") {

    val value = Some("umbrella")
    val expected = DynamoFormat.optionFormat[String].write(value)

    val actual = DynamoFormat[Option[String]].write(value)

    actual should ===(expected)
  }

  test("Derivation should prioritise implicits from user specified companions") {
    val result = DynamoFormat[FoobarAutoDerivation].write(FoobarAutoDerivation(()))

    result should ===(DynamoValue.fromString("foo"))
  }

  test("Derivation with pascalCase naming mode should correctly write field names") {
    final case class Person(name: String, age: Int)

    import org.scanamo.generic.auto.pascalCase._

    val result = DynamoFormat[Person].write(Person("john", 12))

    result should ===(
      DynamoValue.fromDynamoObject(
        DynamoObject(
          "Name" -> DynamoValue.fromString("john"),
          "Age" -> DynamoValue.fromNumber(12)
        )
      )
    )
  }

  test("Derivation with pascalCase naming mode should correctly read field names") {
    final case class Person(name: String, age: Int)

    import org.scanamo.generic.auto.pascalCase._

    val dynamoObject = DynamoValue.fromDynamoObject(
      DynamoObject(
        "Name" -> DynamoValue.fromString("john"),
        "Age" -> DynamoValue.fromNumber(12)
      )
    )
    val result = DynamoFormat[Person].read(dynamoObject)

    result should ===(Right(Person("john", 12)))
  }

  test("Derivation with snakeCase naming mode should correctly write field names") {
    final case class Person(fullName: String, age: Int)

    import org.scanamo.generic.auto.snakeCase._

    val result = DynamoFormat[Person].write(Person("john", 12))

    result should ===(
      DynamoValue.fromDynamoObject(
        DynamoObject(
          "full_name" -> DynamoValue.fromString("john"),
          "age" -> DynamoValue.fromNumber(12)
        )
      )
    )
  }

  test("Derivation with snakeCase naming mode should correctly read field names") {
    final case class Person(fullName: String, age: Int)

    import org.scanamo.generic.auto.snakeCase._

    val dynamoObject = DynamoValue.fromDynamoObject(
      DynamoObject(
        "full_name" -> DynamoValue.fromString("john"),
        "age" -> DynamoValue.fromNumber(12)
      )
    )
    val result = DynamoFormat[Person].read(dynamoObject)

    result should ===(Right(Person("john", 12)))
  }

  test("Derivation with kebabCase naming mode should correctly write field names") {
    final case class Person(fullName: String, age: Int)

    import org.scanamo.generic.auto.kebabCase._

    val result = DynamoFormat[Person].write(Person("john", 12))

    result should ===(
      DynamoValue.fromDynamoObject(
        DynamoObject(
          "full-name" -> DynamoValue.fromString("john"),
          "age" -> DynamoValue.fromNumber(12)
        )
      )
    )
  }

  test("Derivation with kebabCase naming mode should correctly read field names") {
    final case class Person(fullName: String, age: Int)

    import org.scanamo.generic.auto.kebabCase._

    val dynamoObject = DynamoValue.fromDynamoObject(
      DynamoObject(
        "full-name" -> DynamoValue.fromString("john"),
        "age" -> DynamoValue.fromNumber(12)
      )
    )
    val result = DynamoFormat[Person].read(dynamoObject)

    result should ===(Right(Person("john", 12)))
  }
}
