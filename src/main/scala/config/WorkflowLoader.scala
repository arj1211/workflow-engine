package config

import engine.{Workflow, StepNode}
import upickle.default.*

import java.nio.file.{Files, Path}
import scala.util.{Try, Success, Failure}

object WorkflowLoader {

  // Load from a JSON string
  def fromJson(
      json: String,
      registry: StepRegistry
  ): Either[String, Workflow] = {
    for {
      // Parse JSON into our config case classes
      config <- parseJson(json)
      // Convert config into engine's Workflow
      workflow <- buildWorkflow(config, registry)
      // Validate the resulting workflow
      _ <- workflow.validate()
    } yield workflow
    // ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    // This is a FOR-COMPREHENSION over Either.
    //
    // It works like this:
    //   1. parseJson returns Either[String, WorkflowConfig]
    //   2. If Left(error), the whole thing short-circuits to Left(error)
    //   3. If Right(config), 'config' is extracted and we continue
    //   4. Same for buildWorkflow and validate
    //   5. If everything is Right, we get Right(workflow)
    //
    // It's like chaining .flatMap calls but much more readable.
    // This is one of Scala's most powerful patterns.
  }

  // Load from a file path
  def fromFile(
      path: String,
      registry: StepRegistry
  ): Either[String, Workflow] = {
    readFile(path).flatMap(json => fromJson(json, registry))
    //             ^^^^^^^^
    //  .flatMap on Either: if readFile returns Left(error), stop.
    //  If Right(json), pass the json to fromJson.
  }

  // ── Private helpers ──────────────────────────────────────────

  private def parseJson(json: String): Either[String, WorkflowConfig] = {
    Try(read[WorkflowConfig](json)) match {
      //  ^^^
      //  Try(...) catches any exception and wraps it:
      //    - no exception → Success(value)
      //    - exception    → Failure(exception)
      case Success(config) => Right(config)
      case Failure(ex)     => Left(s"Invalid JSON: ${ex.getMessage}")
    }
  }

  private def buildWorkflow(
      config: WorkflowConfig,
      registry: StepRegistry
  ): Either[String, Workflow] = {

    // Try to create a StepNode for each config entry
    val nodeResults: List[Either[String, (String, StepNode)]] =
      config.nodes.map { nodeConfig =>
        registry.create(nodeConfig.stepType, nodeConfig.config).map { step =>
          nodeConfig.id -> StepNode(
            id = nodeConfig.id,
            step = step,
            dependencies = nodeConfig.dependsOn.toSet
          )
        }
      }

    // Collect results: if ANY failed, return the first error.
    // If all succeeded, combine into a Map.
    val errors = nodeResults.collect { case Left(err) => err }
    //                       ^^^^^^^
    //  .collect with a partial function: keeps only the elements
    //  that match the pattern, and transforms them.
    //  Here: keep only Left values, extract their error strings.

    if (errors.nonEmpty) {
      Left(s"Failed to build workflow:\n${errors.mkString("\n")}")
    } else {
      val nodes = nodeResults.collect { case Right(pair) => pair }.toMap
      Right(Workflow(name = config.name, nodes = nodes))
    }
  }

  private def readFile(path: String): Either[String, String] = {
    Try(Files.readString(Path.of(path))) match {
      case Success(content) => Right(content)
      case Failure(ex) => Left(s"Cannot read file '$path': ${ex.getMessage}")
    }
  }
}
