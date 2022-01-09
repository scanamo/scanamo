package org.scanamo.query

import cats.implicits._
import org.scanamo.DynamoObject

/** A lawless typeclass functionally identical to [[UniqueKeyCondition]] but used to constrain a different set values
  * to be used as index keys.
  */
trait IndexKey[T] {
  type K
  def toDynamoObject(t: T): DynamoObject
  def fromDynamoObject(key: K, dvs: DynamoObject): Option[T]
  def key(t: T): K
}

/** An index key with 3 properties to support paginating indexes whose key structure has a property that does not
  * overlap with that of the parent table.
  */
final case class IndexKey3[A, B, C](a: A, b: B, c: C) {
  def and[D](d: D): IndexKey4[A, B, C, D] = IndexKey4(a, b, c, d)
}

/** An index key with 4 properties to support paginating indexes having both a hash key and a sort key that do not
  * overlap with those of the parent table.
  */
final case class IndexKey4[A, B, C, D](a: A, b: B, c: C, d: D)

object IndexKey {
  type Aux[T, K0] = IndexKey[T] { type K = K0 }

  def toDynamoObject[T, K](t: T)(implicit T: IndexKey.Aux[T, K]): DynamoObject = T.toDynamoObject(t)

  /** Summon of an instance for `IndexKey3`.
    */
  def of3[A, B, C](implicit
    K: IndexKey.Aux[IndexKey3[KeyEquals[A], KeyEquals[B], KeyEquals[C]], (AttributeName, AttributeName, AttributeName)]
  ): Aux[IndexKey3[KeyEquals[A], KeyEquals[B], KeyEquals[C]], (AttributeName, AttributeName, AttributeName)] = K

  /** Summoner of an instance for `IndexKey3` where the types of the underlying properties are the same.
    */
  def of3Hom[A](implicit
    K: IndexKey.Aux[IndexKey3[KeyEquals[A], KeyEquals[A], KeyEquals[A]], (AttributeName, AttributeName, AttributeName)]
  ): Aux[IndexKey3[KeyEquals[A], KeyEquals[A], KeyEquals[A]], (AttributeName, AttributeName, AttributeName)] = K

  def of4[A, B, C, D](implicit
    K: IndexKey.Aux[IndexKey4[KeyEquals[A], KeyEquals[B], KeyEquals[C], KeyEquals[D]],
                    (AttributeName, AttributeName, AttributeName, AttributeName)
    ]
  ): Aux[IndexKey4[KeyEquals[A], KeyEquals[B], KeyEquals[C], KeyEquals[D]],
         (AttributeName, AttributeName, AttributeName, AttributeName)
  ] = K

  def of4Hom[A](implicit
    K: IndexKey.Aux[IndexKey4[KeyEquals[A], KeyEquals[A], KeyEquals[A], KeyEquals[A]],
                    (AttributeName, AttributeName, AttributeName, AttributeName)
    ]
  ): Aux[IndexKey4[KeyEquals[A], KeyEquals[A], KeyEquals[A], KeyEquals[A]],
         (AttributeName, AttributeName, AttributeName, AttributeName)
  ] = K

  /** `IndexKey` instance for `AndEqualsCondition[KeyEquals[H], KeyEquals[S]]` to support established syntax.
    */
  implicit def andEquals[H, S](implicit
    HS: UniqueKeyCondition[AndEqualsCondition[KeyEquals[H], KeyEquals[S]]]
  ): IndexKey[AndEqualsCondition[KeyEquals[H], KeyEquals[S]]] {
    type K = HS.K
  } =
    new IndexKey[AndEqualsCondition[KeyEquals[H], KeyEquals[S]]] {
      type K = HS.K
      final def toDynamoObject(t: AndEqualsCondition[KeyEquals[H], KeyEquals[S]]): DynamoObject =
        HS.toDynamoObject(t)
      final def fromDynamoObject(key: K, dvs: DynamoObject): Option[AndEqualsCondition[KeyEquals[H], KeyEquals[S]]] =
        HS.fromDynamoObject(key, dvs)
      final def key(t: AndEqualsCondition[KeyEquals[H], KeyEquals[S]]): K = HS.key(t)
    }

  /* `IndexKey` instances for `IndexKey3` and `IndexKey4`, supporting established syntax over index keys of 3-4
   * properties. */

  implicit def indexKey3[H, R, S](implicit
    H: UniqueKeyCondition[KeyEquals[H]],
    R: UniqueKeyCondition[KeyEquals[R]],
    S: UniqueKeyCondition[KeyEquals[S]]
  ): IndexKey[IndexKey3[KeyEquals[H], KeyEquals[R], KeyEquals[S]]] { type K = (H.K, R.K, S.K) } =
    new IndexKey[IndexKey3[KeyEquals[H], KeyEquals[R], KeyEquals[S]]] {
      type K = (H.K, R.K, S.K)
      final def toDynamoObject(t: IndexKey3[KeyEquals[H], KeyEquals[R], KeyEquals[S]]): DynamoObject =
        H.toDynamoObject(t.a) <> R.toDynamoObject(t.b) <> S.toDynamoObject(t.c)
      final def fromDynamoObject(key: K,
                                 dvs: DynamoObject
      ): Option[IndexKey3[KeyEquals[H], KeyEquals[R], KeyEquals[S]]] =
        (H.fromDynamoObject(key._1, dvs), R.fromDynamoObject(key._2, dvs), S.fromDynamoObject(key._3, dvs))
          .mapN(IndexKey3(_, _, _))
      final def key(t: IndexKey3[KeyEquals[H], KeyEquals[R], KeyEquals[S]]): K = (H.key(t.a), R.key(t.b), S.key(t.c))
    }

  implicit def indexKey4[TH, TS, IH, IS](implicit
    TH: UniqueKeyCondition[KeyEquals[TH]],
    TS: UniqueKeyCondition[KeyEquals[TS]],
    IH: UniqueKeyCondition[KeyEquals[IH]],
    IS: UniqueKeyCondition[KeyEquals[IS]]
  ): IndexKey[IndexKey4[KeyEquals[TH], KeyEquals[TS], KeyEquals[IH], KeyEquals[IS]]] {
    type K = (TH.K, TS.K, IH.K, IS.K)
  } =
    new IndexKey[IndexKey4[KeyEquals[TH], KeyEquals[TS], KeyEquals[IH], KeyEquals[IS]]] {
      type K = (TH.K, TS.K, IH.K, IS.K)

      final def toDynamoObject(t: IndexKey4[KeyEquals[TH], KeyEquals[TS], KeyEquals[IH], KeyEquals[IS]]): DynamoObject =
        TH.toDynamoObject(t.a) <> TS.toDynamoObject(t.b) <> IH.toDynamoObject(t.c) <> IS.toDynamoObject(t.d)

      final def fromDynamoObject(key: (TH.K, TS.K, IH.K, IS.K),
                                 dvs: DynamoObject
      ): Option[IndexKey4[KeyEquals[TH], KeyEquals[TS], KeyEquals[IH], KeyEquals[IS]]] =
        (
          TH.fromDynamoObject(key._1, dvs),
          TS.fromDynamoObject(key._2, dvs),
          IH.fromDynamoObject(key._3, dvs),
          IS.fromDynamoObject(key._4, dvs)
        )
          .mapN(IndexKey4(_, _, _, _))

      final def key(t: IndexKey4[KeyEquals[TH], KeyEquals[TS], KeyEquals[IH], KeyEquals[IS]]): K =
        (TH.key(t.a), TS.key(t.b), IH.key(t.c), IS.key(t.d))
    }

}
