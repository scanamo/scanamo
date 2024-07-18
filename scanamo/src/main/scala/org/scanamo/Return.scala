/*
 * Copyright 2019 Scanamo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.scanamo

import software.amazon.awssdk.services.dynamodb.model.ReturnValue

sealed abstract class PutReturn extends Product with Serializable { self =>
  import PutReturn.*

  final def asDynamoValue: ReturnValue =
    self match {
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
  import DeleteReturn.*

  final def asDynamoValue: ReturnValue =
    self match {
      case Nothing  => ReturnValue.NONE
      case OldValue => ReturnValue.ALL_OLD
    }
}

object DeleteReturn {
  case object Nothing extends DeleteReturn
  case object OldValue extends DeleteReturn
}
