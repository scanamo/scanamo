package org.scanamo

sealed abstract class DynamoDefault[+A] extends Product with Serializable { self =>
  import DynamoDefault._

  final def orElse[A1 >: A](x: => Option[A1]): Option[A1] =
    self match {
      case NoDef      => None
      case MapDef     => x
      case SomeDef(x) => Some(x)
    }
}

object DynamoDefault {
  case object NoDef extends DynamoDefault[Nothing]
  case object MapDef extends DynamoDefault[Nothing]
  final case class SomeDef[A](x: A) extends DynamoDefault[A]
}
