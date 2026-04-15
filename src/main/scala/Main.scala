import config.*
import engine.*
import logging.*

import scala.concurrent.ExecutionContext

given ExecutionContext = ExecutionContext.global

@main def run(): Unit = {

  val registry = DefaultRegistry.create()
  val engine = LoggedParallelDAGEngine(logger = ConsoleLogger())

  // ─── Demo 1: Load from JSON string ──────────────────────────
  println("=" * 60)
  println("DEMO 1: Workflow from JSON string")
  println("=" * 60)

  val json = """{
    "name": "InlineDemo",
    "nodes": [
      {"id": "fetch", "stepType": "FetchOrder", "config": {}},
      {"id": "validate", "stepType": "ValidateOrder", "dependsOn": ["fetch"], "config": {}},
      {"id": "process", "stepType": "ProcessOrder", "dependsOn": ["validate"], "config": {}}
    ]
  }"""

  WorkflowLoader.fromJson(json, registry) match {
    case Right(workflow) =>
      val (result, log) = engine.run(workflow, WorkflowContext())
      println()
      println(log.summary)
    case Left(error) =>
      println(s"  Failed to load: $error")
  }

  // ─── Demo 2: Load from file ─────────────────────────────────
  println()
  println("=" * 60)
  println("DEMO 2: Workflow from JSON file")
  println("=" * 60)

  WorkflowLoader.fromFile("workflows/order_processing.json", registry) match {
    case Right(workflow) =>
      val (result, log) = engine.run(workflow, WorkflowContext())
      println()
      println(log.summary)
    case Left(error) =>
      println(s"  Failed to load: $error")
  }

  // ─── Demo 3: Parallel benchmark from file ───────────────────
  println()
  println("=" * 60)
  println("DEMO 3: Parallel benchmark from JSON file")
  println("=" * 60)

  WorkflowLoader.fromFile("workflows/parallel_demo.json", registry) match {
    case Right(workflow) =>
      val (result, log) = engine.run(workflow, WorkflowContext())
      println()
      println(log.summary)
      println()
      println("  Step durations:")
      log.stepLogs.sortBy(_.durationMs).reverse.foreach { s =>
        val bar = "█" * (s.durationMs / 10).toInt
        println(f"    ${s.stepName}%-12s ${s.durationMs}%4dms $bar")
      }
    case Left(error) =>
      println(s"  Failed to load: $error")
  }

  // ─── Demo 4: Error handling — bad JSON ──────────────────────
  println()
  println("=" * 60)
  println("DEMO 4: Error handling")
  println("=" * 60)

  println("\n  Bad JSON:")
  WorkflowLoader.fromJson("not valid json", registry) match {
    case Right(_)    => println("  (unexpected success)")
    case Left(error) => println(s"  ✗ $error")
  }

  println("\n  Unknown step type:")
  val badType =
    """{"name":"Bad","nodes":[{"id":"x","stepType":"DoesNotExist","config":{}}]}"""
  WorkflowLoader.fromJson(badType, registry) match {
    case Right(_)    => println("  (unexpected success)")
    case Left(error) => println(s"  ✗ $error")
  }

  println("\n  Missing file:")
  WorkflowLoader.fromFile("workflows/nonexistent.json", registry) match {
    case Right(_)    => println("  (unexpected success)")
    case Left(error) => println(s"  ✗ $error")
  }

  println("\n  Cyclic dependencies:")
  val cyclic = """{
    "name": "Cyclic",
    "nodes": [
      {"id": "a", "stepType": "FetchOrder", "dependsOn": ["c"], "config": {}},
      {"id": "b", "stepType": "FetchOrder", "dependsOn": ["a"], "config": {}},
      {"id": "c", "stepType": "FetchOrder", "dependsOn": ["b"], "config": {}}
    ]
  }"""
  WorkflowLoader.fromJson(cyclic, registry) match {
    case Right(_)    => println("  (unexpected success)")
    case Left(error) => println(s"  ✗ $error")
  }

  // ─── Show available step types ──────────────────────────────
  println()
  println("=" * 60)
  println(s"REGISTRY: ${registry.availableTypes.size} step types available")
  println("=" * 60)
  registry.availableTypes.toList.sorted.foreach { t =>
    println(s"  • $t")
  }
}
