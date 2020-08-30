package org.scanamo.joda

package org.scanamo.joda

 import org.joda.time.{ DateTime, Instant }

 import org.scanamo.DynamoFormat
 import org.joda.time.DateTime
 import org.joda.time.chrono.ISOChronology
 import org.scanamo.joda.TimeGenerators._
 import org.scanamo.joda.JodaFormats._

 import org.scalacheck._
 import org.scalacheck.Prop.forAll

 class JodaFormatsTest extends Properties("DynamoValue") {
   property("Instant round trip") =
     forAll((v: Instant) => DynamoFormat[Instant].read(DynamoFormat[Instant].write(v)) == Right(v))

   property("DateTime round trip") = forAll { (dt: DateTime) =>
     val dtBasic = dt.withChronology(ISOChronology.getInstanceUTC())
     DynamoFormat[DateTime].read(DynamoFormat[DateTime].write(dtBasic)) == Right(dtBasic)
   }
 }