package org.scanamo.generic

import org.scalatest.NonImplicitAssertions
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scanamo.{ DynamoFormat, DynamoObject, DynamoValue }

class SemiAutoDerivationTest extends AnyFunSuite with Matchers with NonImplicitAssertions {
  test("Derivation should fail if no derived format or automatic derivation") {
    """write(Person("Alice", 65))""" shouldNot compile
  }

  test("Derivation should not switch to automatic for class members") {
    """
       import org.scanamo._
       import org.scanamo.generic.semiauto._
       implicit val failingFormat: DynamoFormat[User] = deriveDynamoFormat[User]
       """ shouldNot compile
  }

  test("Derivation should succeed if derived format in scope") {
    """
      import org.scanamo._
      import org.scanamo.generic.semiauto._
      implicit val formatLocationInfo: DynamoFormat[LocationInfo] = deriveDynamoFormat[LocationInfo]
      implicit val formatUser: DynamoFormat[User] = deriveDynamoFormat[User]

      write(User(Some(1), true, "Bob", "Geldorf", "pink", "1234", None, Some(LocationInfo(Some("UK"), None, None, None))))
      """ should compile
  }

  test("Derivation should prioritise implicits from user specified companions") {
    val result = DynamoFormat[FoobarSemiAutoDerivation].write(FoobarSemiAutoDerivation(Some("this is a foo")))

    result should ===(DynamoValue.fromDynamoObject(DynamoObject("value" -> DynamoValue.fromString("this is a foo"))))
  }

  def write[T](t: T)(implicit f: DynamoFormat[T]): DynamoValue = f.write(t)
}

case class Person(name: String, age: Int)

trait UserShape {
  val id: Option[Long]
  val isActiveUser: Boolean
  val firstName: String
  val lastName: String
  val userSlug: String
  val hashedPassword: String
  val phone: Option[String]
  val locationInfo: Option[LocationInfo]
}

case class User(
  override val id: Option[Long],
  override val isActiveUser: Boolean,
  override val firstName: String,
  override val lastName: String,
  override val userSlug: String,
  override val hashedPassword: String,
  override val phone: Option[String],
  override val locationInfo: Option[LocationInfo]
) extends UserShape

case class LocationInfo(nation: Option[String],
                        provState: Option[String],
                        postalCode: Option[String],
                        preferredLocale: Option[String]
)
