package engine

import logging.*
import java.time.Instant
import scala.concurrent.{Future, Await, ExecutionContext}
import scala.concurrent.duration.*
import scala.util.{Try, Success, Failure}

class LoggedParallelDAGEngine(
    logger: Logger = ConsoleLogger(),
    levelTimeout: Duration = 60.seconds
)(using ec: ExecutionContext) {

  // Returns BOTH the workflow result AND the structured log
  def run(
      workflow: Workflow,
      ctx: WorkflowContext
  ): (WorkflowResult, WorkflowLog) = {

    val collector = LogCollector(workflow.name)

    // Validate
    workflow.validate() match {
      case Left(err) =>
        logger.error(s"Workflow validation failed: $err")
        val result = WorkflowResult(false, ctx, Some(err))
        return (result, collector.build())
      case Right(_) =>
    }

    val levels = workflow.executionLevels()

    // Log the plan
    val planStr = levels
      .map { level =>
        if (level.size == 1) level.head
        else s"[${level.mkString(" | ")}]"
      }
      .mkString(" → ")
    logger.info(s"Workflow '${workflow.name}' starting")
    logger.info(s"Plan: $planStr")

    var currentCtx = ctx
    var completed = List.empty[String]
    var failed = false

    for ((level, levelIndex) <- levels.zipWithIndex if !failed) {
      //                                             ^^^^^^^^^^
      //  'if !failed' is a GUARD on the for loop.
      //  If 'failed' becomes true, remaining levels are skipped.

      val isParallel = level.size > 1

      if (isParallel) {
        logger.info(s"Level $levelIndex: ${level.size} steps in parallel")

        val futures = level.toList.map { nodeId =>
          val node = workflow.nodes(nodeId)
          val future = Future {
            val start = Instant.now()
            val thread = Thread.currentThread().getName
            logger.debug(s"${node.step.name} started on $thread")

            val result = node.step.execute(currentCtx)
            val end = Instant.now()

            val log = result match {
              case Right(_) =>
                StepLog(
                  nodeId,
                  node.step.name,
                  StepStatus.Success,
                  start,
                  end,
                  thread
                )
              case Left(err) =>
                StepLog(
                  nodeId,
                  node.step.name,
                  StepStatus.Failed(err),
                  start,
                  end,
                  thread
                )
            }
            collector.addLog(log)
            (nodeId, result, log)
          }
          (nodeId, future)
        }

        // Wait for all
        val allResults = futures.map { case (nodeId, future) =>
          Try(Await.result(future, levelTimeout)) match {
            case Success(tuple) => tuple
            case Failure(ex)    =>
              val now = Instant.now()
              val log = StepLog(
                nodeId,
                nodeId,
                StepStatus.Failed(ex.getMessage),
                now,
                now
              )
              collector.addLog(log)
              (nodeId, Left(ex.getMessage), log)
          }
        }

        // Process results
        for ((nodeId, result, log) <- allResults) {
          result match {
            case Right(newCtx) =>
              logger.info(s"${log.stepName} ✓ (${log.durationMs}ms)")
              currentCtx = WorkflowContext(currentCtx.allData ++ newCtx.allData)
              completed = completed :+ nodeId
            case Left(err) =>
              logger.error(s"${log.stepName} ✗ (${log.durationMs}ms): $err")
              failed = true
          }
        }

      } else {
        // Single step
        val nodeId = level.head
        val node = workflow.nodes(nodeId)
        val start = Instant.now()

        logger.debug(s"${node.step.name} starting")
        val result = node.step.execute(currentCtx)
        val end = Instant.now()

        val log = result match {
          case Right(newCtx) =>
            val l =
              StepLog(nodeId, node.step.name, StepStatus.Success, start, end)
            collector.addLog(l)
            logger.info(s"${node.step.name} ✓ (${l.durationMs}ms)")
            currentCtx = WorkflowContext(currentCtx.allData ++ newCtx.allData)
            completed = completed :+ nodeId
            l
          case Left(err) =>
            val l = StepLog(
              nodeId,
              node.step.name,
              StepStatus.Failed(err),
              start,
              end
            )
            collector.addLog(l)
            logger.error(s"${node.step.name} ✗ (${l.durationMs}ms): $err")
            failed = true
            l
        }
      }
    }

    val workflowLog = collector.build()
    logger.info(s"Workflow '${workflow.name}' ${
        if (failed) "FAILED" else "COMPLETED"
      } in ${workflowLog.totalMs}ms")

    val result = WorkflowResult(
      success = !failed,
      finalContext = currentCtx,
      error = if (failed) Some("Workflow had failures") else None,
      completedSteps = completed
    )

    (result, workflowLog)
  }
}
