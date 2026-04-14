import engine.*
import steps.*
import logging.*
import scala.concurrent.ExecutionContext

given ExecutionContext = ExecutionContext.global

@main def run(): Unit = {

  // ─── Demo 1: Logged parallel execution ──────────────────────
  println("=" * 60)
  println("DEMO 1: Logged Parallel Execution")
  println("=" * 60)

  val workflow = Workflow(
    name = "ParallelDemo",
    nodes = Map(
      "start" -> StepNode(
        "start",
        new TimedStep("Start", 100, ParallelDemoKeys.StepD, "started")
      ),
      "a" -> StepNode(
        "a",
        new TimedStep("TaskA", 300, ParallelDemoKeys.StepA, "done_a"),
        dependencies = Set("start")
      ),
      "b" -> StepNode(
        "b",
        new TimedStep("TaskB", 200, ParallelDemoKeys.StepB, "done_b"),
        dependencies = Set("start")
      ),
      "c" -> StepNode(
        "c",
        new TimedStep("TaskC", 400, ParallelDemoKeys.StepC, "done_c"),
        dependencies = Set("start")
      ),
      "finish" -> StepNode(
        "finish",
        new TimedStep("Finish", 50, ParallelDemoKeys.Final, "complete"),
        dependencies = Set("a", "b", "c")
      )
    )
  )

  val engine = LoggedParallelDAGEngine(logger = ConsoleLogger())
  val (result, log) = engine.run(workflow, WorkflowContext())

  // Print the structured summary
  println()
  println(log.summary)

  // ─── Demo 2: Programmatic access to log data ───────────────
  println()
  println("=" * 60)
  println("DEMO 2: Programmatic Log Access")
  println("=" * 60)

  println(s"  Total steps:    ${log.stepLogs.size}")
  println(s"  All succeeded:  ${log.succeeded}")
  println(s"  Total time:     ${log.totalMs}ms")
  log.slowestStep.foreach { s =>
    println(s"  Slowest step:   ${s.stepName} (${s.durationMs}ms)")
  }
  println(s"  Step durations:")
  log.stepLogs.sortBy(_.durationMs).reverse.foreach { s =>
    //         ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    //  Sort by duration, longest first
    val bar = "█" * (s.durationMs / 10).toInt // simple bar chart!
    println(f"    ${s.stepName}%-12s ${s.durationMs}%4dms $bar")
    //        ^^^^^^^^^^^^^^^^^^
    //  f"..." is a format string (like printf).
    //  %-12s = left-aligned string, 12 chars wide
    //  %4d   = right-aligned integer, 4 digits wide
  }

  // ─── Demo 3: Silent logger (for benchmarking) ──────────────
  println()
  println("=" * 60)
  println("DEMO 3: Silent Logger (no output, just data)")
  println("=" * 60)

  val silentEngine = LoggedParallelDAGEngine(logger = SilentLogger())
  val (_, silentLog) = silentEngine.run(workflow, WorkflowContext())
  println(s"  Ran silently. Total time: ${silentLog.totalMs}ms")
  println(s"  Steps completed: ${silentLog.stepLogs.count(_.succeeded)}")

  // ─── Demo 4: Buffered logger (for testing) ─────────────────
  println()
  println("=" * 60)
  println("DEMO 4: Buffered Logger (capture for assertions)")
  println("=" * 60)

  val buffered = BufferedLogger()
  val testEngine = LoggedParallelDAGEngine(logger = buffered)
  testEngine.run(workflow, WorkflowContext())

  println(s"  Captured ${buffered.messages.size} log messages")
  println(s"  Errors: ${buffered.messages.count(_._1 == "ERROR")}")
  println(s"  Sample messages:")
  buffered.messages.take(3).foreach { case (level, msg) =>
    println(s"    [$level] $msg")
  }
}
