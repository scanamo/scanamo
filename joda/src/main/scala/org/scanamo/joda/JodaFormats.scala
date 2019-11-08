package org.scanamo.joda

import org.scanamo.DynamoFormat
import org.joda.time.{ DateTime, Instant }

object JodaFormats {
  /**  Format for dealing with points in time stored as the number of milliseconds since Epoch.
    *  {{{
    *  prop> import org.joda.time.Instant
    *  prop> import org.scanamo.DynamoFormat
    *  prop> import org.scanamo.joda.TimeGenerators._
    *  prop> import org.scanamo.joda.JodaFormats._
    *  prop> (x: Instant) =>
    *      | DynamoFormat[Instant].read(DynamoFormat[Instant].write(x)) == Right(x)
    *  }}}
    */
  implicit val jodaInstantAsLongFormat =
    DynamoFormat.coercedXmap[Instant, Long, ArithmeticException](new Instant(_))(
      x => x.getMillis
    )

  /**
    *  Convenient, readable format for Joda DateTime, but requires that all dates serialised
    *  have a consistent chronology and time zone.
    *
    *  {{{
    *  prop> import org.scanamo.DynamoFormat
    *  prop> import org.joda.time.DateTime
    *  prop> import org.joda.time.chrono.ISOChronology
    *  prop> import org.scanamo.joda.TimeGenerators._
    *  prop> import org.scanamo.joda.JodaFormats.jodaStringFormat
    *  prop> (dt: DateTime) =>
    *      | val dtBasic = dt.withChronology(ISOChronology.getInstanceUTC())
    *      | DynamoFormat[DateTime].read(DynamoFormat[DateTime].write(dtBasic)) == Right(dtBasic)
    *  }}}
    */
  implicit val jodaStringFormat = DynamoFormat.coercedXmap[DateTime, String, IllegalArgumentException](
    DateTime.parse(_)
  )(
    _.toString
  )
}
