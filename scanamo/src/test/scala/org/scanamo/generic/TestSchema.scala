package org.scanamo.generic



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

sealed trait ExampleEnum
case object First extends ExampleEnum

