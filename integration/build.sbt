ThisBuild / scalaVersion := "2.13.12"
ThisBuild / organization := "com.canton.integration"
ThisBuild / version      := "0.1.0"

val pekkoVersion     = "1.0.2"
val pekkoHttpVersion = "1.0.1"
val circeVersion     = "0.14.6"

lazy val root = (project in file("."))
  .settings(
    name := "canton-ledger-integration",
    scalacOptions ++= Seq("-deprecation", "-feature", "-Xfatal-warnings:false"),
    libraryDependencies ++= Seq(
      // Pekko (Apache-licensed Akka fork): streaming + HTTP, mirroring the
      // gRPC-streaming style of the real Daml Ledger API Scala bindings
      // (com.daml.ledger.api.v1.{CommandSubmissionServiceGrpc, TransactionServiceGrpc}).
      "org.apache.pekko" %% "pekko-actor-typed" % pekkoVersion,
      "org.apache.pekko" %% "pekko-stream"      % pekkoVersion,
      "org.apache.pekko" %% "pekko-http"        % pekkoHttpVersion,

      // JSON for the HTTP gateway bridge consumed by the Java backend.
      "io.circe" %% "circe-core"    % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser"  % circeVersion,
      "de.heikoseeberger" %% "pekko-http-circe" % "1.39.2",

      "ch.qos.logback" % "logback-classic" % "1.4.14",

      "org.scalatest" %% "scalatest" % "3.2.18" % Test,
      "org.apache.pekko" %% "pekko-testkit" % pekkoVersion % Test,
      "org.apache.pekko" %% "pekko-http-testkit" % pekkoHttpVersion % Test
    )
  )
