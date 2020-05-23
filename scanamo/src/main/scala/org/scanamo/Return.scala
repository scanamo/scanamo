package org.scanamo

import software.amazon.awssdk.services.dynamodb.model.ReturnValue

sealed abstract class PutReturn extends Product with Serializable { self =>
  import PutReturn._

  final def asDynamoValue: ReturnValue = self match {
    case Nothing  => ReturnValue.NONE
    case OldValue => ReturnValue.ALL_OLD
    case NewValue => ReturnValue.ALL_NEW
  }
}

object PutReturn {
  case object Nothing extends PutReturn
  case object OldValue extends PutReturn
  case object NewValue extends PutReturn
}

sealed abstract class DeleteReturn extends Product with Serializable { self =>
  import DeleteReturn._

  final def asDynamoValue: ReturnValue = self match {
    case Nothing  => ReturnValue.NONE
    case OldValue => ReturnValue.ALL_OLD
  }
}

object DeleteReturn {
  case object Nothing extends DeleteReturn
  case object OldValue extends DeleteReturn
}
