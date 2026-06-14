package com.canton.integration.domain

import java.time.Instant

/**
 * Mirrors the shape of `com.daml.ledger.api.v1.CommandSubmissionService` /
 * `TransactionService` payloads (CreateCommand, ExerciseCommand,
 * Transaction, Event) as exposed by real Daml/Canton Scala bindings.
 *
 * In a production deployment these would be generated Protobuf case classes
 * (via scalapb) from `com/daml/ledger/api/v1/commands.proto` and
 * `transaction.proto`. Here we hand-roll a minimal, structurally similar
 * set of types so the [[com.canton.integration.CantonLedgerApiClient]]
 * trait and its simulated implementation can demonstrate the same
 * command-submission / transaction-streaming contract that a real bank
 * integration would code against.
 */

/** Alias for a Daml contract identifier (opaque string in the gRPC API). */
final case class ContractId(value: String) {
  override def toString: String = value
}

/** A fully-qualified Daml template identifier, e.g. "Repo.Repo:RepoProposal". */
final case class TemplateId(value: String) {
  override def toString: String = value
}

/** Identifies the submitting party/parties and an application-supplied command id. */
final case class CommandMeta(
    applicationId: String,
    commandId: String,
    actAs: List[String],
    readAs: List[String] = Nil,
    submittedAt: Instant = Instant.now()
)

/** Base type for ledger commands, mirroring `Command` (oneof Create/Exercise/...) in commands.proto. */
sealed trait LedgerCommand

/** Mirrors `CreateCommand`: create a new contract instance of `templateId` with `payload`. */
final case class CreateCommand(templateId: TemplateId, payload: Map[String, Any]) extends LedgerCommand

/** Mirrors `ExerciseCommand`: exercise `choice` on `contractId` with `argument`. */
final case class ExerciseCommand(
    templateId: TemplateId,
    contractId: ContractId,
    choice: String,
    argument: Map[String, Any]
) extends LedgerCommand

/** Mirrors `ArchiveCommand` (sugar for exercising the implicit `Archive` choice). */
final case class ArchiveCommand(templateId: TemplateId, contractId: ContractId) extends LedgerCommand

/** A full command submission, mirroring `SubmitRequest`. */
final case class CommandSubmission(meta: CommandMeta, commands: List[LedgerCommand])

/** Mirrors `Completion`: the asynchronous result of a command submission. */
final case class Completion(commandId: String, success: Boolean, status: String, transactionId: Option[String])

/** Base type for ledger events within a transaction, mirroring `Event` (oneof Created/Archived). */
sealed trait LedgerEvent {
  def contractId: ContractId
  def templateId: TemplateId
}

/** Mirrors `CreatedEvent`. */
final case class CreatedEvent(
    contractId: ContractId,
    templateId: TemplateId,
    signatories: List[String],
    observers: List[String],
    payload: Map[String, Any]
) extends LedgerEvent

/** Mirrors `ArchivedEvent`. */
final case class ArchivedEvent(
    contractId: ContractId,
    templateId: TemplateId,
    witnessParties: List[String]
) extends LedgerEvent

/** Mirrors `Transaction`: an ordered batch of events committed atomically. */
final case class LedgerTransaction(
    transactionId: String,
    commandId: String,
    workflowId: String,
    effectiveAt: Instant,
    events: List[LedgerEvent]
)
