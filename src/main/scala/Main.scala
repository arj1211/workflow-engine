import engine.*
import steps.*
import logging.*
import persistence.*
import config.*
import scala.concurrent.ExecutionContext

given ExecutionContext = ExecutionContext.global

@main def run(): Unit = {

  // ─── Demo 1: Crash and Resume ───────────────────────────────
  println("=" * 60)
  println("DEMO 1: Crash and Resume")
  println("=" * 60)

  // Set up persistence
  val stateStore = FileStateStore("checkpoints")
  val engine = DurableDAGEngine(
    stateStore = stateStore,
    logger = ConsoleLogger()
  )

  // Clear any previous checkpoints for clean demo
  stateStore.clear("order-001")
  CrashTracker.reset()

  // Define a workflow where step "process" will crash
  val crashKeys = new {
    val Fetched: TypedKey[String] = TypedKey[String]("fetched")
    val Validated: TypedKey[String] = TypedKey[String]("validated")
    val Inventoried: TypedKey[String] = TypedKey[String]("inventoried")
    val Processed: TypedKey[String] = TypedKey[String]("processed")
    val Notified: TypedKey[String] = TypedKey[String]("notified")
  }

  val workflow = Workflow(
    name = "CrashDemo",
    nodes = Map(
      "fetch" -> StepNode(
        "fetch",
        new TimedStep("FetchOrder", 100, crashKeys.Fetched, "fetched")
      ),
      "validate" -> StepNode(
        "validate",
        new TimedStep("Validate", 150, crashKeys.Validated, "validated"),
        dependencies = Set("fetch")
      ),
      "inventory" -> StepNode(
        "inventory",
        new TimedStep("CheckInventory", 200, crashKeys.Inventoried, "checked"),
        dependencies = Set("fetch")
      ),
      "process" -> StepNode(
        "process",
        new CrashingStep("ProcessOrder", crashKeys.Processed, "processed"),
        dependencies = Set("validate", "inventory")
      ),
      "notify" -> StepNode(
        "notify",
        new TimedStep("Notify", 50, crashKeys.Notified, "notified"),
        dependencies = Set("process")
      )
    )
  )

  // FIRST RUN: will crash at "process"
  println("\n--- FIRST RUN (will crash at ProcessOrder) ---\n")
  val (result1, log1) = engine.run(workflow, WorkflowContext(), "order-001")
  println()
  println(log1.summary)

  // Show what's on disk
  println("\n--- Checkpoint on disk ---")
  stateStore.load("order-001") match {
    case Right(Some(state)) =>
      println(s"  Status: ${state.status}")
      println(s"  Completed: ${state.completedSteps.mkString(", ")}")
      println(s"  Context keys: ${state.contextData.keys.mkString(", ")}")
    case other =>
      println(s"  $other")
  }

  // SECOND RUN: resume from checkpoint — "process" succeeds this time
  println("\n--- SECOND RUN (resume from checkpoint) ---\n")
  val (result2, log2) = engine.run(workflow, WorkflowContext(), "order-001")
  println()
  println(log2.summary)

  // ─── Demo 2: In-Memory Store (for fast testing) ─────────────
  println()
  println("=" * 60)
  println("DEMO 2: In-Memory State Store")
  println("=" * 60)

  val memStore = InMemoryStateStore()
  val memEngine = DurableDAGEngine(
    stateStore = memStore,
    logger = ConsoleLogger()
  )
  CrashTracker.reset()

  // Simple workflow that completes successfully
  val simpleWorkflow = Workflow(
    name = "SimpleFlow",
    nodes = Map(
      "a" -> StepNode(
        "a",
        new TimedStep("StepA", 100, TypedKey[String]("a"), "done")
      ),
      "b" -> StepNode(
        "b",
        new TimedStep("StepB", 100, TypedKey[String]("b"), "done"),
        dependencies = Set("a")
      )
    )
  )

  val (result3, log3) =
    memEngine.run(simpleWorkflow, WorkflowContext(), "test-001")
  println()
  println(log3.summary)

  // Check in-memory store
  memStore.load("test-001") match {
    case Right(Some(state)) =>
      println(s"\n  In-memory state: ${state.status}")
      println(s"  Completed: ${state.completedSteps.mkString(", ")}")
    case other =>
      println(s"\n  $other")
  }

  // ─── Demo 3: List all checkpoints ──────────────────────────
  println()
  println("=" * 60)
  println("DEMO 3: List saved checkpoints")
  println("=" * 60)

  stateStore.listAll() match {
    case Right(ids) =>
      println(s"  Found ${ids.size} checkpoint(s) on disk:")
      ids.foreach { id =>
        stateStore.load(id) match {
          case Right(Some(state)) =>
            println(
              s"    • $id: ${state.status}, ${state.completedSteps.size} steps done"
            )
          case _ =>
            println(s"    • $id: (could not load)")
        }
      }
    case Left(err) =>
      println(s"  Error: $err")
  }
}
