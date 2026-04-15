package config

import engine.WorkflowStep

// A factory function: takes a config map, returns a WorkflowStep.
// Every registered step type has one of these.
type StepFactory = Map[String, String] => WorkflowStep

// The registry: maps step type names to their factory functions.
//
// This is the BRIDGE between the JSON world (strings) and
// the Scala world (typed objects).
//
// When JSON says {"stepType": "FetchOrder"}, the registry
// looks up "FetchOrder" and calls its factory to create
// an actual FetchOrderStep instance.

class StepRegistry {
  private var factories = Map.empty[String, StepFactory]

  // Register a step type. Called during setup.
  def register(stepType: String)(factory: StepFactory): StepRegistry = {
    factories = factories + (stepType -> factory)
    this // return 'this' for method chaining
    //      registry.register("A")(f1).register("B")(f2)
  }

  // Create a step instance from a type name + config.
  // Returns Either so we get a clear error instead of an exception.
  def create(
      stepType: String,
      config: Map[String, String]
  ): Either[String, WorkflowStep] = {
    factories.get(stepType) match {
      case Some(factory) =>
        try Right(factory(config))
        catch {
          case e: Exception =>
            Left(s"Failed to create '$stepType': ${e.getMessage}")
        }
      case None =>
        Left(
          s"Unknown step type: '$stepType'. Available: ${factories.keys.mkString(", ")}"
        )
    }
  }

  def availableTypes: Set[String] = factories.keySet
}
