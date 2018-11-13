package com.gu.scanamo

import cats.syntax.either._

import com.gu.scanamo.error.{DynamoReadError, TypeCoercionError}
import com.amazonaws.services.dynamodbv2.model.AttributeValue

import eu.timepit.refined.api.{RefType, Validate}

package object refined {

  implicit def refTypeDynamoFormat[F[_, _], T, P](
    implicit
    baseFormat: DynamoFormat[T],
    refType: RefType[F],
    validate: Validate[T, P]
  ): DynamoFormat[F[T, P]] = new DynamoFormat[F[T, P]] {

    override def default: Option[F[T, P]] =
      baseFormat.default.flatMap(refType.refine[P](_).toOption)

    def read(av: AttributeValue): Either[DynamoReadError, F[T, P]] =
      baseFormat.read(av).flatMap { v =>
        refType.refine[P](v).leftMap(desc => TypeCoercionError(new Exception(desc)))
      }

    def write(v: F[T, P]): AttributeValue =
      baseFormat.write(refType.unwrap(v))

  }

}
