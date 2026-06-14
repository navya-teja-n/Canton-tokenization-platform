package com.canton.integration

import com.canton.integration.domain._
import org.apache.pekko.actor.testkit.typed.scaladsl.ActorTestKit
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.Await
import scala.concurrent.duration._

class SimulatedCantonLedgerApiClientSpec
    extends ActorTestKit
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll {

  implicit val typedSystem = this.system

  private val client = new SimulatedCantonLedgerApiClient()

  private val depositTemplate = TemplateId("Assets.Deposit:TokenizedDeposit")

  "SimulatedCantonLedgerApiClient" should {

    "create a contract and make it visible to its signatories and observers" in {
      val meta = CommandMeta(applicationId = "canton-integration-test", commandId = "cmd-1", actAs = List("Bank1"))
      val create = CreateCommand(
        templateId = depositTemplate,
        payload = Map(
          "bank" -> "Bank1",
          "owner" -> "Alice",
          "regulator" -> "Regulator1",
          "depositId" -> "DEP-1",
          "currency" -> "USD",
          "amount" -> BigDecimal("1000.00"),
          "signatories" -> List("Bank1", "Alice"),
          "observers" -> List("Regulator1")
        )
      )

      val completion = Await.result(client.submit(CommandSubmission(meta, List(create))), 5.seconds)
      completion.success shouldBe true

      val asBank1 = Await.result(client.activeContracts("Bank1", depositTemplate), 5.seconds)
      val asAlice = Await.result(client.activeContracts("Alice", depositTemplate), 5.seconds)
      val asRegulator = Await.result(client.activeContracts("Regulator1", depositTemplate), 5.seconds)
      val asStranger = Await.result(client.activeContracts("Stranger", depositTemplate), 5.seconds)

      asBank1 should have size 1
      asAlice should have size 1
      asRegulator should have size 1
      asStranger shouldBe empty
    }

    "archive a contract via ExerciseCommand and remove it from the active contract set" in {
      val meta = CommandMeta(applicationId = "canton-integration-test", commandId = "cmd-2", actAs = List("Bank1"))
      val create = CreateCommand(
        templateId = depositTemplate,
        payload = Map(
          "bank" -> "Bank1",
          "owner" -> "Bob",
          "depositId" -> "DEP-2",
          "currency" -> "USD",
          "amount" -> BigDecimal("500.00"),
          "signatories" -> List("Bank1", "Bob")
        )
      )
      val createCompletion = Await.result(client.submit(CommandSubmission(meta, List(create))), 5.seconds)
      val createdEvent = Await.result(client.activeContracts("Bob", depositTemplate), 5.seconds).head
      createCompletion.success shouldBe true

      val exercise = ExerciseCommand(depositTemplate, createdEvent.contractId, "Freeze", Map.empty)
      val exerciseMeta = meta.copy(commandId = "cmd-3")
      val exerciseCompletion = Await.result(client.submit(CommandSubmission(exerciseMeta, List(exercise))), 5.seconds)

      exerciseCompletion.success shouldBe true
      Await.result(client.lookupContract("Bob", createdEvent.contractId), 5.seconds) shouldBe None
    }
  }

  override def afterAll(): Unit = shutdownTestKit()
}
