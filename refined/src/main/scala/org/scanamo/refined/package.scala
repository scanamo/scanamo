package org.scanamo

import cats.syntax.either._

import org.scanamo.error.{ DynamoReadError, TypeCoercionError }

import eu.timepit.refined.api.{ RefType, Validate }

package object refined {

  implicit def refTypeDynamoFormat[F[_, _], T, P](
    implicit
    baseFormat: DynamoFormat[T],
    refType: RefType[F],
    validate: Validate[T, P]
  ): DynamoFormat[F[T, P]] = new DynamoFormat[F[T, P]] {

    final override val default: Option[F[T, P]] =
      baseFormat.default.flatMap(refType.refine[P](_).toOption)

    final def read(av: DynamoValue): Either[DynamoReadError, F[T, P]] =
      baseFormat.read(av).flatMap { v =>
        refType.refine[P](v).leftMap(desc => TypeCoercionError(new Exception(desc)))
      }

    final def write(v: F[T, P]): DynamoValue =
      baseFormat.write(refType.unwrap(v))

  }

}
