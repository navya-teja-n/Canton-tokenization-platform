package com.canton.integration

import com.canton.integration.domain._
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source

import scala.concurrent.Future

/**
 * Trait mirroring the two core services of the Daml Ledger API that real
 * Canton-connected institutions program against:
 *
 *  - `CommandSubmissionService` (here: `submit`) -- asynchronous,
 *    idempotent-by-`commandId` submission of create/exercise/archive
 *    commands.
 *  - `TransactionService` (here: `transactions`) -- a server-streaming feed
 *    of committed transactions, used by downstream systems (settlement
 *    monitors, risk engines, data lakes) to react to ledger state changes.
 *
 * A production implementation would wrap a generated gRPC stub
 * (`com.daml.ledger.api.v1.CommandSubmissionServiceGrpc.CommandSubmissionServiceStub`
 * and `TransactionServiceGrpc.TransactionServiceStub`) using Pekko gRPC.
 * [[SimulatedCantonLedgerApiClient]] provides an in-memory implementation
 * with identical semantics for this demo, so the rest of the integration
 * layer (and the Java backend, via [[gateway.IntegrationGateway]]) can be
 * exercised end-to-end without a real Canton node.
 */
trait CantonLedgerApiClient {

  /** Submit a batch of commands. Completes with a [[Completion]] once the ledger commits (or rejects) the transaction. */
  def submit(submission: CommandSubmission): Future[Completion]

  /** Stream all transactions visible to `party`, optionally starting after `afterTransactionId`. */
  def transactions(party: String, afterTransactionId: Option[String] = None): Source[LedgerTransaction, NotUsed]

  /** Active-contract-set lookup: all currently-active contracts of `templateId` visible to `party`. */
  def activeContracts(party: String, templateId: TemplateId): Future[Seq[CreatedEvent]]

  /** Fetch a single active contract by id, if it is currently active and visible to `party`. */
  def lookupContract(party: String, contractId: ContractId): Future[Option[CreatedEvent]]
}
