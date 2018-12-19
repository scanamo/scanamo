package com.gu.scanamo

import com.gu.scanamo.Derivation.SemiAutoDerivation

/**
  * Semi-automatic format derivation.
  *
  * This object provides helpers for creating [[com.gu.scanamo.DynamoFormat]]
  * instances for case classes
  *
  * Typical usage will look like the following:
  *
  * {{{
  * import com.gu.scanamo.semiauto._
  *
  * case class Bear(name: String, favouriteFood: String)
  * object Bear {
  *   implicit val formatBear: DynamoFormat[Bear] = deriveDynamoFormat[Bear]
  * }
  * }}}
  */
package object semiauto extends SemiAutoDerivation
