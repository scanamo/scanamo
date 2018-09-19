package com.gu.scanamo.generic

import com.gu.scanamo.{DerivedDynamoFormat, DynamoFormat}
import com.gu.scanamo.export.Exported
import shapeless._

/**
 * Fully automatic configurable codec derivation.
 *
 * Extending this trait provides [[io.circe.Decoder]] and [[io.circe.Encoder]]
 * instances for case classes (if all members have instances), "incomplete" case classes, sealed
 * trait hierarchies, etc.
 */
object auto extends DerivedDynamoFormat {
  implicit def genericProductAuto[T, R](
    implicit
    notSymbol: NotSymbol[T],
    gen: LabelledGeneric.Aux[T, R],
    formatR: Lazy[ValidConstructedDynamoFormat[R]]
  ): Exported[DynamoFormat[T]] =
    Exported(genericProduct(notSymbol, gen, formatR))

  implicit def genericCoProductAuto[T, R](
    implicit
    gen: LabelledGeneric.Aux[T, R],
    formatR: Lazy[CoProductDynamoFormat[R]]
  ): Exported[DynamoFormat[T]] =
    Exported(genericCoProduct(gen, formatR))
}
