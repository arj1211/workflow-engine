package engine

import logging.*
import persistence.*
import java.time.Instant
import scala.concurrent.{Future, Await, ExecutionContext}
import scala.concurrent.duration.*
import scala.util.{Try, Success, Failure}

class DurableDAGEngine(
    stateStore: StateStore,
    logger: Logger = ConsoleLogger(),
    levelTimeout: Duration = 60.seconds
)(using ec: ExecutionContext) {

  // Run a workflow with persistence.
  // If a checkpoint exists for this workflowId, RESUME from it.
  // Otherwise, start fresh.
  def run(
      workflow: Workflow,
      ctx: WorkflowContext,
      workflowId: String
  ): (WorkflowResult, WorkflowLog) = {

    val collector = LogCollector(workflow.name)

    // Validate
    workflow.validate() match {
      case Left(err) =>
        logger.error(s"Validation failed: $err")
        val result = WorkflowResult(false, ctx, Some(err))
        return (result, collector.build())
      case Right(_) =>
    }

    // Check for existing checkpoint
    val (startCtx, alreadyCompleted) = stateStore.load(workflowId) match {
      case Right(Some(state)) =>
        // RESUMING from checkpoint!
        val restoredCtx = ContextSerializer.deserialize(state.contextData)
        logger.info(
          s"Resuming '$workflowId' — ${state.completedSteps.size} steps already done"
        )
        logger.info(
          s"Completed previously: ${state.completedSteps.mkString(", ")}"
        )
        (restoredCtx, state.completedSteps.toSet)

      case Right(None) =>
        // Fresh start
        logger.info(s"Starting fresh: '$workflowId'")
        (ctx, Set.empty[String])

      case Left(err) =>
        logger.warn(s"Could not load checkpoint: $err — starting fresh")
        (ctx, Set.empty[String])
    }

    val levels = workflow.executionLevels()

    val planStr = levels
      .map { level =>
        if (level.size == 1) level.head
        else s"[${level.mkString(" | ")}]"
      }
      .mkString(" → ")
    logger.info(s"Plan: $planStr")

    var currentCtx = startCtx
    var completed = alreadyCompleted.toList
    var failed = false

    for ((level, levelIndex) <- levels.zipWithIndex if !failed) {

      // Skip levels where ALL steps are already completed
      val stepsToRun = level -- alreadyCompleted
      //               ^^^^^^^^^^^^^^^^^^^^^^^^
      //  Set difference: remove steps we've already done

      if (stepsToRun.isEmpty) {
        logger.info(s"Level $levelIndex: all steps already completed, skipping")
        // Still need to log them as skipped
        level.foreach { nodeId =>
          val node = workflow.nodes(nodeId)
          val now = Instant.now()
          collector.addLog(
            StepLog(
              nodeId,
              node.step.name,
              StepStatus.Skipped("previously completed"),
              now,
              now
            )
          )
        }
      } else {
        // Some or all steps in this level need to run
        val isParallel = stepsToRun.size > 1

        if (isParallel) {
          logger.info(
            s"Level $levelIndex: ${stepsToRun.size} steps in parallel"
          )

          val futures = stepsToRun.toList.map { nodeId =>
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

          for ((nodeId, result, log) <- allResults) {
            result match {
              case Right(newCtx) =>
                logger.info(s"${log.stepName} ✓ (${log.durationMs}ms)")
                currentCtx = WorkflowContext(
                  currentCtx.allData ++ newCtx.allData
                )
                completed = completed :+ nodeId
              case Left(err) =>
                logger.error(s"${log.stepName} ✗: $err")
                failed = true
            }
          }

        } else {
          // Single step (might be only remaining in a partially-completed level)
          val nodeId = stepsToRun.head
          val node = workflow.nodes(nodeId)
          val start = Instant.now()
          logger.debug(s"${node.step.name} starting")
          val result = node.step.execute(currentCtx)
          val end = Instant.now()

          result match {
            case Right(newCtx) =>
              val log =
                StepLog(nodeId, node.step.name, StepStatus.Success, start, end)
              collector.addLog(log)
              logger.info(s"${node.step.name} ✓ (${log.durationMs}ms)")
              currentCtx = WorkflowContext(currentCtx.allData ++ newCtx.allData)
              completed = completed :+ nodeId
            case Left(err) =>
              val log = StepLog(
                nodeId,
                node.step.name,
                StepStatus.Failed(err),
                start,
                end
              )
              collector.addLog(log)
              logger.error(s"${node.step.name} ✗: $err")
              failed = true
          }
        }

        // ─── CHECKPOINT after each level ────────────────────
        // This is the key persistence operation.
        val status =
          if (failed) WorkflowStatus.Failed("Step failure")
          else WorkflowStatus.Running
        val checkpoint = WorkflowState(
          workflowName = workflow.name,
          workflowId = workflowId,
          completedSteps = completed,
          contextData = ContextSerializer.serialize(currentCtx),
          status = status
        )
        stateStore.save(checkpoint) match {
          case Right(_) =>
            logger.debug(s"Checkpoint saved: ${completed.size} steps completed")
          case Left(err) =>
            logger.warn(s"Failed to save checkpoint: $err")
        }
      }
    }

    // Final state
    val finalStatus =
      if (failed) WorkflowStatus.Failed("Workflow failed")
      else WorkflowStatus.Completed
    val finalState = WorkflowState(
      workflowName = workflow.name,
      workflowId = workflowId,
      completedSteps = completed,
      contextData = ContextSerializer.serialize(currentCtx),
      status = finalStatus
    )
    stateStore.save(finalState)

    // Clean up checkpoint on success
    if (!failed) {
      logger.info(
        "Workflow completed successfully — checkpoint retained for reference"
      )
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
