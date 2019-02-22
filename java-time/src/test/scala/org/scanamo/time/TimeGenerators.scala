package org.scanamo.time

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

  implicit val zonedDateTimeArb: Arbitrary[ZonedDateTime] = Arbitrary {
    for {
      year <- Gen.choose[Int](Year.MIN_VALUE, Year.MAX_VALUE)
      month <- Gen.choose[Int](1, 12)
      days = YearMonth.of(year, month).lengthOfMonth
      day <- Gen.choose[Int](1, days)
      hour <- Gen.choose[Int](0, 23)
      minute <- Gen.choose[Int](0, 59)
      second <- Gen.choose[Int](0, 59)
      milli <- Gen.choose[Int](0, 999)
      offsetH <- Gen.choose[Int](-18, 18)
      offsetM <- if (offsetH == 18 || offsetH == -18) Gen.const(0) else if (offsetH < 0) Gen.choose[Int](-59, -1) else Gen.choose(0, 59)
      offsetS <- if (offsetH == 18 || offsetH == -18) Gen.const(0) else if (offsetH < 0) Gen.choose[Int](-59, -1) else Gen.choose(0, 59)
    } yield ZonedDateTime.of(year, month, day, hour, minute, second, milli * 1000000, ZoneOffset.ofHoursMinutesSeconds(offsetH, offsetM, offsetS))
  }

  implicit val instantAsLongArb: Arbitrary[Instant] = Arbitrary {
    for {
      l <- arbitrary[Long]
    } yield Instant.ofEpochMilli(l)
  }
}
