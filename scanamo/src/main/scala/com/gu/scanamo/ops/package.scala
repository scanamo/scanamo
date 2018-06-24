package com.gu.scanamo

import cats.free.Free

package object ops {
  type ScanamoOps[A] = Free[ScanamoOpsA, A]
}
