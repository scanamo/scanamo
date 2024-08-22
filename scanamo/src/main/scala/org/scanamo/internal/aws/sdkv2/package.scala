package org.scanamo.internal.aws

import org.scanamo.internal.aws.sdkv2.HasExpressionAttributes.{ Foo, Moo }
import org.scanamo.request.{ AttributesSummation, RequestCondition }
import org.scanamo.internal.aws.sdkv2.HasExpressionAttributes.*

package object sdkv2 {

  implicit class RichBuilder2[T, B <: Moo[T]](builder: B) {
    def funk(as: AttributesSummation)(implicit h: HasExpressionAttributes[B]): T =
      builder.tableName(as.tableName).attributes(as.attributes).build()
  }

  implicit class RichBuilder[B <: Foo](builder: B) {
    def setOpt[V](opt: Option[V])(f: B => V => B): B = opt.foldLeft(builder) { (b, v) =>
      f(b)(v)
    }

    def set[V](v: V)(f: B => V => B): B = f(builder)(v)

    def expression(c: RequestCondition)(f: B => String => B): B = f(builder)(c.expression)
  }
}
