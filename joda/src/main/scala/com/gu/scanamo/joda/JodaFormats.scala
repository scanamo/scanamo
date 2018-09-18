package com.gu.scanamo.joda

import com.gu.scanamo.DynamoFormat
import org.joda.time.DateTime

object JodaFormats {

  /**
   *  Convenient, readable format for Joda DateTime, but requires that all dates serialised
   *  have a consistent chronology and time zone.
   *
   *  {{{
   *  prop> import com.gu.scanamo.DynamoFormat
   *  prop> import org.joda.time.DateTime
   *  prop> import org.joda.time.chrono.ISOChronology
   *  prop> import com.fortysevendeg.scalacheck.datetime.joda.ArbitraryJoda._
   *  prop> import com.gu.scanamo.joda.JodaFormats.jodaStringFormat
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

  /**
   *  {{{
   *  prop> import com.gu.scanamo.DynamoFormat
   *  prop> import org.joda.time.DateTime
   *  prop> import com.fortysevendeg.scalacheck.datetime.joda.ArbitraryJoda._
   *  prop> import com.gu.scanamo.joda.JodaFormats.jodaEpochSecondsFormat
   *  prop> (dt: DateTime) =>
   *      | DynamoFormat[DateTime].read(DynamoFormat[DateTime].write(dt)) == Right(dt)
   *  }}}
   */
  implicit val jodaEpochSecondsFormat = DynamoFormat.coercedXmap[DateTime, Long, IllegalArgumentException](
    new DateTime(_)
  )(
    _.getMillis()
  )
}
