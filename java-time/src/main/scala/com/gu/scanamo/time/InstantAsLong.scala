package com.gu.scanamo.time

import java.time.Instant

object InstantAsLongs {
  sealed abstract class InstantAsLongImpl {
    type T
    def apply(x: Instant): T
    def unwrap(x: T): Instant
  }

  val InstantAsLong: InstantAsLongImpl = new InstantAsLongImpl {
    type T = Instant
    def apply(x: Instant) = x
    def unwrap(x: T) = x
  }

  type InstantAsLong = InstantAsLong.T
}

