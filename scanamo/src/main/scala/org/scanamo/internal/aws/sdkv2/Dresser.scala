package org.scanamo.internal.aws.sdkv2

import cats.Endo
import org.scanamo.internal.aws.sdkv2.HasCondition.*
import org.scanamo.internal.aws.sdkv2.HasItem.*
import org.scanamo.internal.aws.sdkv2.HasKey.*
import org.scanamo.internal.aws.sdkv2.HasUpdateAndCondition.HasUpdateAndConditionOps
import org.scanamo.request.*
import org.scanamo.request.AWSSdkV2.{ De, Pu, Up }

trait Dresser[C <: CRUD, T[_]] {
  def blast[B: T](crud: C): Endo[B]
}

object Dresser {

  implicit val deleteDresser: Dresser[Deleting, De] = new Dresser[Deleting, De] {
    override def blast[B: De](crud: Deleting): Endo[B] = _.key(crud).setOptionalCondition(crud)
  }

  implicit val updateDresser: Dresser[Updating, Up] = new Dresser[Updating, Up] {
    override def blast[B: Up](crud: Updating): Endo[B] = _.key(crud).updateAndCondition(crud.updateAndCondition)
  }

  implicit val puttingDresser: Dresser[Putting, Pu] = new Dresser[Putting, Pu] {
    override def blast[B: Pu](crud: Putting): Endo[B] = _.item(crud).setOptionalCondition(crud)
  }
}
