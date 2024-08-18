package org.scanamo.internal.aws

import org.scanamo.request.RequestCondition
import software.amazon.awssdk.utils.builder.Buildable

package object sdkv2 {
  implicit class RichBuilder[B <: Buildable](builder: B) {
    def setOpt[V](opt: Option[V])(f: B => V => B): B = opt.foldLeft(builder) { (b, v) =>
      f(b)(v)
    }

    def expression(c: RequestCondition)(f: B => String => B): B = f(builder)(c.expression)
  }
}
