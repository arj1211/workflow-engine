package engine

import scala.concurrent.{Future, Await, ExecutionContext}
import scala.concurrent.duration.*
import scala.util.{Try, Success, Failure}

// The execution context is the thread pool that runs our Futures.
// By taking it as a parameter (using 'using' in Scala 3),
// the caller decides what thread pool to use.
//
// 'using' is Scala 3's way of saying "pass this automatically
// if one is available in scope." You'll see how at the call site.

class ParallelDAGEngine(using ec: ExecutionContext) {

  def run(
      workflow: Workflow,
      ctx: WorkflowContext,
      levelTimeout: Duration = 60.seconds // max time per level
  ): WorkflowResult = {

    // Validate first
    workflow.validate() match {
      case Left(err) =>
        return WorkflowResult(
          success = false,
          finalContext = ctx,
          error = Some(err)
        )
      case Right(_) =>
    }

    val levels = workflow.executionLevels()
    val workflowStart = System.nanoTime()

    // Print execution plan
    val planStr = levels
      .map { level =>
        if (level.size == 1) level.head
        else s"[${level.mkString(" | ")}]"
      }
      .mkString(" → ")
    println(s"  Execution plan: $planStr")

    var currentCtx = ctx
    var completed = List.empty[String]

    for ((level, levelIndex) <- levels.zipWithIndex) {
      val levelStart = System.nanoTime()
      val isParallel = level.size > 1
      val hint = if (isParallel) s" ⚡ ${level.size} steps in parallel" else ""
      println(s"\n  ── Level $levelIndex$hint ──")

      if (isParallel) {
        // ─── PARALLEL EXECUTION ─────────────────────────────
        // Launch ALL steps in this level as Futures simultaneously
        val futures: List[(String, Future[Either[String, WorkflowContext]])] =
          level.toList.map { nodeId =>
            val node = workflow.nodes(nodeId)
            val future = Future {
              //        ^^^^^^
              //  This block runs on a DIFFERENT THREAD from the pool.
              //  All futures in this list start at ~the same time.
              val threadName = Thread.currentThread().getName
              println(
                s"    ▶ ${node.step.name} (id: $nodeId) [thread: $threadName]"
              )
              val stepStart = System.nanoTime()
              val result = node.step.execute(currentCtx)
              val stepMs = (System.nanoTime() - stepStart) / 1_000_000
              result match {
                case Right(_) =>
                  println(s"    ✓ $nodeId (${stepMs}ms) [thread: $threadName]")
                case Left(e) =>
                  println(
                    s"    ✗ $nodeId (${stepMs}ms): $e [thread: $threadName]"
                  )
              }
              result
            }
            (nodeId, future)
          }

        // Wait for ALL futures to complete
        //
        // Future.sequence takes List[Future[A]] → Future[List[A]]
        // It creates a single Future that completes when ALL inner
        // futures have completed.
        //
        // But we have List[(String, Future[...])], so we need to
        // handle it slightly differently:

        val allResults: List[(String, Either[String, WorkflowContext])] =
          futures.map { case (nodeId, future) =>
            // Await.result blocks until THIS future is done (or timeout)
            val result = Try(Await.result(future, levelTimeout)) match {
              case Success(either) => either
              case Failure(ex)     =>
                Left(s"Step timed out or crashed: ${ex.getMessage}")
            }
            (nodeId, result)
          }

        // Process all results
        val levelMs = (System.nanoTime() - levelStart) / 1_000_000
        println(s"  ── Level $levelIndex completed in ${levelMs}ms ──")

        // Check for failures, merge successes
        for ((nodeId, result) <- allResults) {
          result match {
            case Right(newCtx) =>
              currentCtx = WorkflowContext(currentCtx.allData ++ newCtx.allData)
              completed = completed :+ nodeId
            case Left(err) =>
              return WorkflowResult(
                success = false,
                finalContext = currentCtx,
                error = Some(s"$nodeId failed: $err"),
                completedSteps = completed
              )
          }
        }

      } else {
        // ─── SINGLE STEP (no need for Future overhead) ──────
        val nodeId = level.head
        val node = workflow.nodes(nodeId)
        println(s"    ▶ ${node.step.name} (id: $nodeId)")
        val stepStart = System.nanoTime()
        val result = node.step.execute(currentCtx)
        val stepMs = (System.nanoTime() - stepStart) / 1_000_000

        result match {
          case Right(newCtx) =>
            println(s"    ✓ $nodeId (${stepMs}ms)")
            currentCtx = WorkflowContext(currentCtx.allData ++ newCtx.allData)
            completed = completed :+ nodeId
          case Left(err) =>
            println(s"    ✗ $nodeId (${stepMs}ms): $err")
            return WorkflowResult(
              success = false,
              finalContext = currentCtx,
              error = Some(s"$nodeId failed: $err"),
              completedSteps = completed
            )
        }
      }
    }

    val totalMs = (System.nanoTime() - workflowStart) / 1_000_000
    println(s"\n  ⏱ Total workflow time: ${totalMs}ms")

    WorkflowResult(
      success = true,
      finalContext = currentCtx,
      completedSteps = completed
    )
  }
}
