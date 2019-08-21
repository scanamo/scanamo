package org.scanamo

import com.amazonaws.services.dynamodbv2.model.ReturnValue

sealed abstract class Return extends Product with Serializable { self =>
  import Return._

  final def asDynamoValue: ReturnValue = self match {
    case Nothing  => ReturnValue.NONE
    case OldValue => ReturnValue.ALL_OLD
    case NewValue => ReturnValue.ALL_NEW
  }
}

object Return {
  case object Nothing extends Return
  case object OldValue extends Return
  case object NewValue extends Return
}
