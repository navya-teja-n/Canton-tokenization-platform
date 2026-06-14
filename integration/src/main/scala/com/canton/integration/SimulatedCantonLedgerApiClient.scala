package com.canton.integration

import com.canton.integration.domain._
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.stream.OverflowStrategy
import org.apache.pekko.stream.scaladsl.{BroadcastHub, Keep, Source}

import java.time.Instant
import java.util.UUID
import java.util.concurrent.{ConcurrentHashMap, CopyOnWriteArrayList}
import scala.jdk.CollectionConverters._
import scala.concurrent.Future
import scala.util.control.NonFatal

/**
 * In-memory simulation of a Canton participant node's Ledger API, covering
 * just the `CantonLedgerApiClient` surface (command submission + an active
 * contract set + a transaction stream).
 *
 * Mirrors the semantics of the Java-side
 * `com.canton.platform.ledger.CantonLedgerSimulator`: both keep an
 * append-only transaction log plus a live "active contract set" map keyed
 * by contract id, and both broadcast committed transactions to subscribers.
 * Keeping the two simulators structurally aligned means a payload created
 * via the Java backend and one created via this Scala client are
 * interchangeable from a domain-modelling point of view -- exactly the
 * kind of cross-stack consistency a real institution would enforce by
 * generating both from the same Daml package's `.dar`.
 *
 * Simplification vs. real Daml: `ExerciseCommand` here only archives its
 * target contract. Any contracts a real choice would additionally create
 * must be submitted as sibling `CreateCommand`s in the same
 * [[CommandSubmission]] -- the simulator commits the whole batch as one
 * atomic [[LedgerTransaction]], which is the property that actually matters
 * for downstream consumers of the transaction stream.
 */
final class SimulatedCantonLedgerApiClient(implicit system: ActorSystem[_]) extends CantonLedgerApiClient {

  private implicit val ec: scala.concurrent.ExecutionContext = system.executionContext

  /** Active contract set: contract id -> the CreatedEvent that put it on the ledger. */
  private val acs = new ConcurrentHashMap[ContractId, CreatedEvent]()

  /** Append-only audit log of every committed transaction, in commit order. */
  private val txLog = new CopyOnWriteArrayList[LedgerTransaction]()

  /** Live broadcast of newly-committed transactions to any current subscribers. */
  private val (liveQueue, liveSource) =
    Source.queue[LedgerTransaction](bufferSize = 256, OverflowStrategy.dropHead)
      .toMat(BroadcastHub.sink[LedgerTransaction](bufferSize = 256))(Keep.both)
      .run()

  override def submit(submission: CommandSubmission): Future[Completion] =
    Future {
      this.synchronized {
        val events = submission.commands.flatMap(applyCommand(submission.meta))
        val tx = LedgerTransaction(
          transactionId = UUID.randomUUID().toString,
          commandId = submission.meta.commandId,
          workflowId = submission.meta.commandId,
          effectiveAt = Instant.now(),
          events = events
        )
        txLog.add(tx)
        liveQueue.offer(tx)
        Completion(submission.meta.commandId, success = true, status = "OK", transactionId = Some(tx.transactionId))
      }
    }.recover {
      case NonFatal(e) =>
        Completion(submission.meta.commandId, success = false, status = e.getMessage, transactionId = None)
    }

  override def transactions(party: String, afterTransactionId: Option[String] = None): Source[LedgerTransaction, NotUsed] = {
    val historic = txLog.asScala.toList
    val startIndex = afterTransactionId
      .map(id => historic.indexWhere(_.transactionId == id) + 1)
      .filter(_ > 0)
      .getOrElse(0)
    (Source(historic.drop(startIndex)) ++ liveSource)
      .filter(visibleTo(_, party))
  }

  override def activeContracts(party: String, templateId: TemplateId): Future[Seq[CreatedEvent]] =
    Future.successful {
      acs.values().asScala.iterator
        .filter(e => e.templateId == templateId && isWitness(e.signatories ++ e.observers, party))
        .toSeq
    }

  override def lookupContract(party: String, contractId: ContractId): Future[Option[CreatedEvent]] =
    Future.successful {
      Option(acs.get(contractId)).filter(e => isWitness(e.signatories ++ e.observers, party))
    }

  // -- internals ----------------------------------------------------------

  private def applyCommand(meta: CommandMeta): LedgerCommand => List[LedgerEvent] = {
    case CreateCommand(templateId, payload) =>
      val cid = ContractId(s"${templateId.value}#${UUID.randomUUID()}")
      val signatories = stringList(payload.get("signatories")).getOrElse(meta.actAs)
      val observers = stringList(payload.get("observers")).getOrElse(Nil)
      val created = CreatedEvent(cid, templateId, signatories, observers, payload)
      acs.put(cid, created)
      List(created)

    case ExerciseCommand(templateId, contractId, _choice, _argument) =>
      archive(templateId, contractId)

    case ArchiveCommand(templateId, contractId) =>
      archive(templateId, contractId)
  }

  private def archive(templateId: TemplateId, contractId: ContractId): List[LedgerEvent] =
    Option(acs.remove(contractId)) match {
      case Some(existing) =>
        List(ArchivedEvent(contractId, templateId, existing.signatories ++ existing.observers))
      case None =>
        throw new NoSuchElementException(s"Contract $contractId is not active (already archived or never existed)")
    }

  private def isWitness(parties: List[String], party: String): Boolean = parties.contains(party)

  private def visibleTo(tx: LedgerTransaction, party: String): Boolean =
    tx.events.exists {
      case c: CreatedEvent  => isWitness(c.signatories ++ c.observers, party)
      case a: ArchivedEvent => isWitness(a.witnessParties, party)
    }

  @SuppressWarnings(Array("unchecked"))
  private def stringList(value: Option[Any]): Option[List[String]] = value match {
    case Some(xs: Seq[_]) => Some(xs.map(_.toString).toList)
    case _                => None
  }
}
