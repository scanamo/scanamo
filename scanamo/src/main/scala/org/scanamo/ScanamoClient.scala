package org.scanamo

import cats.{ ~>, Monad }
import org.scanamo.ops.{ ScanamoOps, ScanamoOpsA, ScanamoOpsT }

/** A common super-class for all Scanamo client classes. Use one of the convenience constructors in `ScanamoAsync`,
  * `Scanamo`, `ScanamoCats`, `ScanamoZio`, `ScanamoPekko`, etc, to make an instance of this class.
  *
  * The `exec()` method is the main entry point for executing Scanamo operations.
  */
class ScanamoClient[F[_]: Monad](interpreter: ScanamoOpsA ~> F) {

  /** Execute the operations built with [[org.scanamo.Table]]
    */
  def exec[A](op: ScanamoOps[A]): F[A] = op.foldMap(interpreter)

  /** Execute the operations built with [[org.scanamo.Table]] with effects in the monad `M` threaded in.
    */
  def execT[M[_]: Monad, A](hoist: F ~> M)(op: ScanamoOpsT[M, A]): M[A] =
    op.foldMap(interpreter andThen hoist)
}
