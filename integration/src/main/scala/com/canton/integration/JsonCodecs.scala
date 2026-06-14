package com.canton.integration

import com.canton.integration.domain._
import io.circe._
import io.circe.generic.semiauto._

import java.time.{Instant, LocalDate}
import scala.util.Try

/**
 * Circe JSON codecs for the domain and ledger-command/event model.
 *
 * Real Daml Ledger API Scala bindings exchange Protobuf messages over gRPC;
 * the JSON shapes here are what [[gateway.IntegrationGateway]] exposes to
 * the Java Spring Boot backend (and to any other JVM or non-JVM caller)
 * over plain HTTP, which is the integration style most banks actually use
 * for cross-team / cross-language service boundaries even when the ledger
 * itself speaks gRPC.
 */
object JsonCodecs {

  // -- primitives ---------------------------------------------------------

  implicit val instantEncoder: Encoder[Instant] = Encoder.encodeString.contramap(_.toString)
  implicit val instantDecoder: Decoder[Instant] = Decoder.decodeString.emapTry(s => Try(Instant.parse(s)))

  implicit val localDateEncoder: Encoder[LocalDate] = Encoder.encodeString.contramap(_.toString)
  implicit val localDateDecoder: Decoder[LocalDate] = Decoder.decodeString.emapTry(s => Try(LocalDate.parse(s)))

  implicit val contractIdEncoder: Encoder[ContractId] = Encoder.encodeString.contramap(_.value)
  implicit val contractIdDecoder: Decoder[ContractId] = Decoder.decodeString.map(ContractId.apply)

  implicit val templateIdEncoder: Encoder[TemplateId] = Encoder.encodeString.contramap(_.value)
  implicit val templateIdDecoder: Decoder[TemplateId] = Decoder.decodeString.map(TemplateId.apply)

  // -- enums (encoded as their simple case-object name) -------------------

  private def enumEncoder[A]: Encoder[A] = Encoder.encodeString.contramap(_.toString.split('.').last.split('$').last)

  implicit val assetClassEncoder: Encoder[AssetClass] = enumEncoder[AssetClass]
  implicit val assetClassDecoder: Decoder[AssetClass] = Decoder.decodeString.emap {
    case "GovernmentBond"   => Right(AssetClass.GovernmentBond)
    case "TreasuryBill"     => Right(AssetClass.TreasuryBill)
    case "TokenizedDeposit" => Right(AssetClass.TokenizedDeposit)
    case other              => Left(s"Unknown AssetClass: $other")
  }

  implicit val instrumentStatusEncoder: Encoder[InstrumentStatus] = enumEncoder[InstrumentStatus]
  implicit val instrumentStatusDecoder: Decoder[InstrumentStatus] = Decoder.decodeString.emap {
    case "Active"         => Right(InstrumentStatus.Active)
    case "Collateralized" => Right(InstrumentStatus.Collateralized)
    case "Matured"        => Right(InstrumentStatus.Matured)
    case "Redeemed"       => Right(InstrumentStatus.Redeemed)
    case "Defaulted"      => Right(InstrumentStatus.Defaulted)
    case other            => Left(s"Unknown InstrumentStatus: $other")
  }

  implicit val tradeStatusEncoder: Encoder[TradeStatus] = enumEncoder[TradeStatus]
  implicit val tradeStatusDecoder: Decoder[TradeStatus] = Decoder.decodeString.emap {
    case "Proposed"  => Right(TradeStatus.Proposed)
    case "Accepted"  => Right(TradeStatus.Accepted)
    case "Settled"   => Right(TradeStatus.Settled)
    case "Rejected"  => Right(TradeStatus.Rejected)
    case "Cancelled" => Right(TradeStatus.Cancelled)
    case other       => Left(s"Unknown TradeStatus: $other")
  }

  implicit val repoStatusEncoder: Encoder[RepoStatus] = enumEncoder[RepoStatus]
  implicit val repoStatusDecoder: Decoder[RepoStatus] = Decoder.decodeString.emap {
    case "RepoOpen"      => Right(RepoStatus.RepoOpen)
    case "RepoMatured"   => Right(RepoStatus.RepoMatured)
    case "RepoClosed"    => Right(RepoStatus.RepoClosed)
    case "RepoDefaulted" => Right(RepoStatus.RepoDefaulted)
    case other           => Left(s"Unknown RepoStatus: $other")
  }

  implicit val kycStatusEncoder: Encoder[KycStatus] = enumEncoder[KycStatus]
  implicit val kycStatusDecoder: Decoder[KycStatus] = Decoder.decodeString.emap {
    case "KycPending"  => Right(KycStatus.KycPending)
    case "KycApproved" => Right(KycStatus.KycApproved)
    case "KycRejected" => Right(KycStatus.KycRejected)
    case "KycRevoked"  => Right(KycStatus.KycRevoked)
    case other         => Left(s"Unknown KycStatus: $other")
  }

  implicit val repoDirectionEncoder: Encoder[RepoDirection] = enumEncoder[RepoDirection]
  implicit val repoDirectionDecoder: Decoder[RepoDirection] = Decoder.decodeString.emap {
    case "Repo"       => Right(RepoDirection.Repo)
    case "ReverseRepo" => Right(RepoDirection.ReverseRepo)
    case other        => Left(s"Unknown RepoDirection: $other")
  }

  // -- Money ----------------------------------------------------------------

  implicit val moneyEncoder: Encoder[Money] = deriveEncoder[Money]
  implicit val moneyDecoder: Decoder[Money] = deriveDecoder[Money]

  // -- dynamic command/contract payloads (Map[String, Any] <-> Json) -------

  /** Converts an arbitrary decoded JSON value into Scala values usable as a Daml record payload. */
  def jsonToAny(json: Json): Any = json.fold(
    jsonNull = null,
    jsonBoolean = identity,
    jsonNumber = n => n.toBigDecimal.getOrElse(n.toDouble),
    jsonString = identity,
    jsonArray = arr => arr.map(jsonToAny).toList,
    jsonObject = obj => obj.toMap.view.mapValues(jsonToAny).toMap
  )

  /** Converts a Daml record payload (built from JSON, primitives, lists, maps) back into [[Json]]. */
  def anyToJson(value: Any): Json = value match {
    case null                 => Json.Null
    case j: Json              => j
    case s: String            => Json.fromString(s)
    case b: Boolean           => Json.fromBoolean(b)
    case bd: BigDecimal       => Json.fromBigDecimal(bd)
    case i: Int               => Json.fromInt(i)
    case l: Long              => Json.fromLong(l)
    case d: Double            => Json.fromDoubleOrString(d)
    case xs: Seq[_]           => Json.fromValues(xs.map(anyToJson))
    case m: Map[_, _]         => Json.fromFields(m.map { case (k, v) => k.toString -> anyToJson(v) })
    case other                => Json.fromString(other.toString)
  }

  implicit val payloadEncoder: Encoder[Map[String, Any]] = Encoder.instance(m => anyToJson(m))
  implicit val payloadDecoder: Decoder[Map[String, Any]] = Decoder.decodeJsonObject.map { obj =>
    obj.toMap.view.mapValues(jsonToAny).toMap
  }

  // -- command meta / submission / completion ------------------------------

  implicit val commandMetaEncoder: Encoder[CommandMeta] = deriveEncoder[CommandMeta]
  implicit val commandMetaDecoder: Decoder[CommandMeta] = deriveDecoder[CommandMeta]

  implicit val createCommandEncoder: Encoder[CreateCommand] = deriveEncoder[CreateCommand]
  implicit val createCommandDecoder: Decoder[CreateCommand] = deriveDecoder[CreateCommand]

  implicit val exerciseCommandEncoder: Encoder[ExerciseCommand] = deriveEncoder[ExerciseCommand]
  implicit val exerciseCommandDecoder: Decoder[ExerciseCommand] = deriveDecoder[ExerciseCommand]

  implicit val archiveCommandEncoder: Encoder[ArchiveCommand] = deriveEncoder[ArchiveCommand]
  implicit val archiveCommandDecoder: Decoder[ArchiveCommand] = deriveDecoder[ArchiveCommand]

  /** Tagged-union encoding for [[LedgerCommand]]: `{"kind":"create"|"exercise"|"archive", ...fields}`. */
  implicit val ledgerCommandEncoder: Encoder[LedgerCommand] = Encoder.instance {
    case c: CreateCommand   => createCommandEncoder(c).mapObject(_.add("kind", Json.fromString("create")))
    case c: ExerciseCommand => exerciseCommandEncoder(c).mapObject(_.add("kind", Json.fromString("exercise")))
    case c: ArchiveCommand  => archiveCommandEncoder(c).mapObject(_.add("kind", Json.fromString("archive")))
  }
  implicit val ledgerCommandDecoder: Decoder[LedgerCommand] = Decoder.instance { cursor =>
    cursor.get[String]("kind").flatMap {
      case "create"   => createCommandDecoder(cursor)
      case "exercise" => exerciseCommandDecoder(cursor)
      case "archive"  => archiveCommandDecoder(cursor)
      case other      => Left(DecodingFailure(s"Unknown LedgerCommand kind: $other", cursor.history))
    }
  }

  implicit val commandSubmissionEncoder: Encoder[CommandSubmission] = deriveEncoder[CommandSubmission]
  implicit val commandSubmissionDecoder: Decoder[CommandSubmission] = deriveDecoder[CommandSubmission]

  implicit val completionEncoder: Encoder[Completion] = deriveEncoder[Completion]
  implicit val completionDecoder: Decoder[Completion] = deriveDecoder[Completion]

  // -- events / transactions --------------------------------------------------

  implicit val createdEventEncoder: Encoder[CreatedEvent] =
    deriveEncoder[CreatedEvent].mapJsonObject(_.add("eventType", Json.fromString("created")))
  implicit val archivedEventEncoder: Encoder[ArchivedEvent] =
    deriveEncoder[ArchivedEvent].mapJsonObject(_.add("eventType", Json.fromString("archived")))

  implicit val ledgerEventEncoder: Encoder[LedgerEvent] = Encoder.instance {
    case c: CreatedEvent  => createdEventEncoder(c)
    case a: ArchivedEvent => archivedEventEncoder(a)
  }

  implicit val ledgerTransactionEncoder: Encoder[LedgerTransaction] = deriveEncoder[LedgerTransaction]
}
