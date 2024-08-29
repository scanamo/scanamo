package org.scanamo.internal.aws

import org.scanamo.request.RequestCondition

package object sdkv2 {
  implicit class RichBuilder[B](builder: B) {
    def setOpt[V](opt: Option[V])(f: B => V => B): B = opt.foldLeft(builder) { (b, v) =>
      f(b)(v)
    }

    def set[V](v: V)(f: B => V => B): B = f(builder)(v)

    def expression(c: RequestCondition)(f: B => String => B): B = f(builder)(c.expression)
  }
}
