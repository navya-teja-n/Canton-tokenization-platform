package com.canton.integration.gateway

import com.canton.integration.{CantonLedgerApiClient, JsonCodecs}
import com.canton.integration.domain.{ContractId, TemplateId}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import org.apache.pekko.http.scaladsl.model.sse.ServerSentEvent
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.marshalling.sse.EventStreamMarshalling._

/**
 * HTTP gateway exposing this Scala [[CantonLedgerApiClient]] to the rest of
 * the platform -- most notably the Java Spring Boot backend
 * (`com.canton.platform.*`), which can call these endpoints the same way it
 * would call any other internal microservice.
 *
 * This is the realistic JVM-interop shape for a bank that runs its core
 * Canton/Daml integration in Scala (as Canton itself, and Daml's official
 * Ledger API bindings, are written in Scala) while line-of-business
 * applications are written in Java/Spring: the Scala layer owns the
 * gRPC/Ledger API conversation with the participant node, and exposes a
 * small, stable internal HTTP/JSON contract for everything else. Teams that
 * prefer a single-JVM deployment can instead depend on this module as a
 * library and call [[CantonLedgerApiClient]] directly -- both options are
 * documented in `docs/LEDGER_INTEGRATION.md`.
 *
 * Routes:
 *  - `POST /v1/commands/submit`            submit a [[com.canton.integration.domain.CommandSubmission]], returns a [[com.canton.integration.domain.Completion]]
 *  - `GET  /v1/contracts/active`            query params `party`, `templateId` -> active contracts of that template visible to `party`
 *  - `GET  /v1/contracts/lookup`            query params `party`, `contractId` -> a single contract, if active and visible
 *  - `GET  /v1/transactions/stream`         query param `party` (optional `afterTransactionId`) -> Server-Sent-Events stream of [[com.canton.integration.domain.LedgerTransaction]]
 *  - `GET  /v1/health`                      liveness probe
 */
final class IntegrationGateway(client: CantonLedgerApiClient) {

  import JsonCodecs._

  val routes: Route =
    pathPrefix("v1") {
      concat(
        path("health") {
          get {
            complete(io.circe.Json.obj("status" -> io.circe.Json.fromString("UP")))
          }
        },
        pathPrefix("commands") {
          path("submit") {
            post {
              entity(as[com.canton.integration.domain.CommandSubmission]) { submission =>
                onSuccess(client.submit(submission)) { completion =>
                  complete(completion)
                }
              }
            }
          }
        },
        pathPrefix("contracts") {
          concat(
            path("active") {
              get {
                parameters("party", "templateId") { (party, templateId) =>
                  onSuccess(client.activeContracts(party, TemplateId(templateId))) { events =>
                    complete(events)
                  }
                }
              }
            },
            path("lookup") {
              get {
                parameters("party", "contractId") { (party, contractId) =>
                  onSuccess(client.lookupContract(party, ContractId(contractId))) {
                    case Some(event) => complete(event)
                    case None         => complete(org.apache.pekko.http.scaladsl.model.StatusCodes.NotFound)
                  }
                }
              }
            }
          )
        },
        pathPrefix("transactions") {
          path("stream") {
            get {
              parameters("party", "afterTransactionId".?) { (party, after) =>
                val source = client.transactions(party, after)
                  .map(tx => ServerSentEvent(ledgerTransactionEncoder(tx).noSpaces, "transaction"))
                complete(source)
              }
            }
          }
        }
      )
    }
}
