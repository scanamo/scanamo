package org.scanamo.joda

import org.joda.time.chrono.ISOChronology
import org.joda.time.{ DateTime, Instant }
import org.scalacheck.*
import org.scalacheck.Prop.forAll
import org.scanamo.DynamoFormat
import org.scanamo.joda.JodaFormats.*
import org.scanamo.joda.TimeGenerators.*

class JodaFormatsTest extends Properties("DynamoValue") {
  property("Instant round trip") =
    forAll((v: Instant) => DynamoFormat[Instant].read(DynamoFormat[Instant].write(v)) == Right(v))

  property("DateTime round trip") = forAll { (dt: DateTime) =>
    val dtBasic = dt.withChronology(ISOChronology.getInstanceUTC())
    DynamoFormat[DateTime].read(DynamoFormat[DateTime].write(dtBasic)) == Right(dtBasic)
  }
}
