package com.gu.scanamo.time

import java.time._
import org.scalacheck._
import Gen._
import Arbitrary.arbitrary

object TimeGenerators {
  implicit val offsetDateTimeArb: Arbitrary[OffsetDateTime] = Arbitrary {
    for {
      year <- Gen.choose[Int](Year.MIN_VALUE, Year.MAX_VALUE)
      month <- Gen.choose[Int](1, 12)
      days = YearMonth.of(year, month).lengthOfMonth
      day <- Gen.choose[Int](1, days)
      hour <- Gen.choose[Int](0, 23)
      minute <- Gen.choose[Int](0, 59)
      second <- Gen.choose[Int](0, 59)
      milli <- Gen.choose[Int](0, 999)
    } yield OffsetDateTime.of(year, month, day, hour, minute, second, milli * 1000000, ZoneOffset.UTC)
  }

  implicit val instantAsLongArb: Arbitrary[Instant] = Arbitrary {
    for {
      l <- arbitrary[Long]
    } yield Instant.ofEpochMilli(l)
  }
}
