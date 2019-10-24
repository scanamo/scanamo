package org.scanamo.generic

/**
  * Semi-automatic format derivation.
  *
  * This object provides helpers for creating [[org.scanamo.DynamoFormat]]
  * instances for case classes
  *
  * Typical usage will look like the following:
  *
  * {{{
  * import org.scanamo.generic.semiauto._
  *
  * case class Bear(name: String, favouriteFood: String)
  * object Bear {
  *   implicit val formatBear: DynamoFormat[Bear] = deriveDynamoFormat
  * }
  * }}}
  */
package object semiauto extends SemiautoDerivation
