package com.gu.scanamo.joda

import com.gu.scanamo.DynamoFormat
import org.joda.time.{DateTime, DateTimeZone}

object JodaFormats {
  /**
    *  {{{
    *  prop> import com.gu.scanamo.DynamoFormat
    *  prop> import org.joda.time.DateTime
    *  prop> import com.fortysevendeg.scalacheck.datetime.joda.ArbitraryJoda._
    *  prop> import com.gu.scanamo.joda.JodaFormats.jodaStringFormat
    *  prop> (dt: DateTime) =>
    *      | DynamoFormat[DateTime].read(DynamoFormat[DateTime].write(dt)) == Right(dt)
    *  }}}
    */
  implicit val jodaStringFormat = DynamoFormat.coercedXmap[DateTime, String, IllegalArgumentException](
    DateTime.parse(_).withZone(DateTimeZone.UTC)
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
