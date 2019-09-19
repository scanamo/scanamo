package org.scanamo.joda

import org.joda.time._
import org.scalacheck._
import Arbitrary.arbitrary

object TimeGenerators {
  implicit val dateTimeArb: Arbitrary[DateTime] = Arbitrary {
    for {
      year <- Gen.choose[Int](Years.MIN_VALUE.getYears, Years.MAX_VALUE.getYears)
      month <- Gen.choose[Int](1, 12)
      days = new YearMonth(year, month).monthOfYear.getMaximumValue
      day <- Gen.choose[Int](1, days)
      hour <- Gen.choose[Int](0, 23)
      minute <- Gen.choose[Int](0, 59)
      second <- Gen.choose[Int](0, 59)
      milli <- Gen.choose[Int](0, 999)
    } yield new DateTime(year, month, day, hour, minute, second, milli * 1000000, DateTimeZone.UTC)
  }
}
