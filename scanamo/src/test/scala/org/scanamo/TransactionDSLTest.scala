package org.scanamo

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{EitherValues, NonImplicitAssertions, OptionValues}
import org.scanamo.AlternativeTransacts.Update
import org.scanamo.fixtures.*
import org.scanamo.generic.auto.*
import org.scanamo.request.UpdateExpressionWithCondition
import org.scanamo.syntax.*
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType.*
// import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException

import java.util.UUID

class TransactionDSLTest
    extends AnyFunSpec
    with Matchers
    with NonImplicitAssertions
    with OptionValues
    with EitherValues {
  val client = LocalDynamoDB.syncClient()
  val scanamo = Scanamo(client)

  it("use new Transaction DSL") {
    LocalDynamoDB.usingRandomTable(client)("sortCode" -> N, "accountNumber" -> N) { accountTableName =>
      LocalDynamoDB.usingRandomTable(client)("guid" -> S) { transferTableName =>
        val accountTable = Table[Bank.Account](accountTableName)
        val transferTable = Table[Bank.Transfer](transferTableName)
        implicit val accountKF: KeyFinder[(Int, Int), Bank.Account] = KeyFinder.keyFinderOf("sortCode", "accountNumber")
        implicit val transferKF: KeyFinder[String, Bank.Transfer] = KeyFinder.keyFinderOf("guid")

        def makeTransfer(donorAccount: (Int, Int), recipientAccount: (Int, Int), amount: Int) = {
          val guid = UUID.randomUUID().toString
          ScanamoFree.transact(
            Map(
              accountTable.transForTable(
                Map(
                  donorAccount -> Update(UpdateExpressionWithCondition(add("balance", -amount) and add("transactionIds", Set(guid))))
                    .requiring("balance" >= amount and "frozen" === false),
                  recipientAccount -> Update(UpdateExpressionWithCondition(add("balance", amount) and add("transactionIds", Set(guid)))) //
                    .requiring("frozen" === false)
                )
              ),
              transferTable.transForTable(
                Map(
                  AlternativeTransacts.put(Bank.Transfer(guid, donorAccount, recipientAccount, amount))
                )
              )
            )
          )
        }

        val ops1 = for {
          _ <- accountTable.putAll(
            Set(Bank.Account(100000, 2000000, "Ada", 10), Bank.Account(300001, 4000001, "Byron", 2))
          )
          result <- makeTransfer((100000, 2000000), (300001, 4000001), 6)
        } yield result

        scanamo.exec(ops1) // shouldBe a[Left[TransactionCanceledException, _]]

        val ops2 = for {
          adaAccount <- accountTable.get("sortCode" === 100000 and "accountNumber" === 2000000)
          byronAccount <- accountTable.get("sortCode" === 300001 and "accountNumber" === 4000001)
          transfers <- transferTable.scan()
        } yield (adaAccount, byronAccount, transfers)

        val (adaAccount, byronAccount, transfers) = scanamo.exec(ops2)
        println(adaAccount)

        adaAccount.value.value.balance shouldBe 4
        byronAccount.value.value.balance shouldBe 8
        transfers.size shouldBe 1
      }
    }
  }

}
