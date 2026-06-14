package com.canton.integration

import com.canton.integration.gateway.IntegrationGateway
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

/**
 * Boots the Pekko HTTP gateway in front of [[SimulatedCantonLedgerApiClient]].
 *
 * In a real deployment this `Main` would instead wire up a Pekko/Akka gRPC
 * client stub pointing at a Canton participant's Ledger API port (default
 * `6865`), but the [[CantonLedgerApiClient]] trait and the HTTP contract in
 * [[IntegrationGateway]] are unaffected by that swap -- which is the point
 * of programming against the trait rather than the simulator directly.
 *
 * Run with: `sbt run` (defaults to port 8090, override with `INTEGRATION_HTTP_PORT`).
 */
object Main {

  private val log = LoggerFactory.getLogger(getClass)

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "canton-integration")
    implicit val ec: ExecutionContext = system.executionContext

    val client = new SimulatedCantonLedgerApiClient()
    val gateway = new IntegrationGateway(client)

    val host = sys.env.getOrElse("INTEGRATION_HTTP_HOST", "0.0.0.0")
    val port = sys.env.get("INTEGRATION_HTTP_PORT").flatMap(_.toIntOption).getOrElse(8090)

    Http().newServerAt(host, port).bind(gateway.routes).onComplete {
      case Success(binding) =>
        val addr = binding.localAddress
        log.info("Canton integration gateway listening on http://{}:{}", addr.getHostString, addr.getPort)
        log.info("Realized integration-layer module: Scala Ledger API client + Pekko HTTP gateway for the Java backend")
      case Failure(ex) =>
        log.error("Failed to bind integration gateway", ex)
        system.terminate()
    }
  }
}
