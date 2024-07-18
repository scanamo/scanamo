package org.scanamo.joda

import org.joda.time.*
import org.scalacheck.*
import org.scalacheck.Arbitrary.arbitrary

object TimeGenerators {
  implicit val dateTimeArb: Arbitrary[DateTime] = Arbitrary {
    for {
      year <- Gen.choose[Int](-292275055, 292278994)
      month <- Gen.choose[Int](1, 12)
      days = new DateTime(year, month, 1, 0, 0, 0, 0).dayOfMonth.getMaximumValue
      day <- Gen.choose[Int](1, days)
      hour <- Gen.choose[Int](0, 23)
      minute <- Gen.choose[Int](0, 59)
      second <- Gen.choose[Int](0, 59)
      milli <- Gen.choose[Int](0, 999)
    } yield new DateTime(year, month, day, hour, minute, second, milli, DateTimeZone.UTC)
  }

  implicit val instantAsLongArb: Arbitrary[Instant] = Arbitrary {
    for {
      l <- arbitrary[Long]
    } yield Instant.ofEpochMilli(l)
  }
}
