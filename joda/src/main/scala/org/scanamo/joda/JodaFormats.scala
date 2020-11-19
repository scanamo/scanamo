/*
 * Copyright 2019 Scanamo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.scanamo.joda

import org.scanamo.DynamoFormat
import org.joda.time.{ DateTime, Instant }

object JodaFormats {

  /**  Format for dealing with points in time stored as the number of milliseconds since Epoch.
    */
  implicit val jodaInstantAsLongFormat: DynamoFormat[Instant] =
    DynamoFormat.coercedXmap[Instant, Long, ArithmeticException](new Instant(_), x => x.getMillis)

  /**  Convenient, readable format for Joda DateTime, but requires that all dates serialised
    *  have a consistent chronology and time zone.
    */
  implicit val jodaStringFormat: DynamoFormat[DateTime] =
    DynamoFormat.coercedXmap[DateTime, String, IllegalArgumentException](
      DateTime.parse,
      _.toString
    )
}
