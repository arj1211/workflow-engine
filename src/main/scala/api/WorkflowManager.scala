package api

import engine.*
import config.*
import logging.*
import persistence.*
import scala.concurrent.ExecutionContext

// Manages all workflows: submit, run, check status, get logs.
//
// This is the "brain" that the API calls into.
// It holds all state in thread-safe data structures.
//
// 'synchronized' on every method that touches shared state
// because multiple HTTP requests can arrive simultaneously.

class WorkflowManager(
    registry: StepRegistry,
    stateStore: StateStore
)(using ec: ExecutionContext) {

  // Stored workflows (submitted but maybe not yet run)
  private var workflows = Map.empty[String, Workflow]

  // Logs from completed/failed runs
  private var runLogs = Map.empty[String, WorkflowLog]

  // ── Submit a workflow ─────────────────────────────────────────

  def submit(id: String, json: String): Either[String, WorkflowSubmission] =
    synchronized {
      if (workflows.contains(id)) {
        Left(
          s"Workflow '$id' already exists. DELETE it first or use a different ID."
        )
      } else {
        WorkflowLoader.fromJson(json, registry).map { workflow =>
          workflows = workflows + (id -> workflow)
          WorkflowSubmission(
            id = id,
            name = workflow.name,
            nodeCount = workflow.nodes.size,
            status = "submitted"
          )
        }
      }
    }

  // ── Run a workflow ────────────────────────────────────────────

  def run(id: String): Either[String, WorkflowRunResponse] = {
    // Get the workflow (synchronized)
    val workflow = synchronized {
      workflows.get(id) match {
        case Some(wf) => Right(wf)
        case None     => Left(s"Workflow '$id' not found. Submit it first.")
      }
    }

    workflow.flatMap { wf =>
      // Run OUTSIDE synchronized — execution can take a long time
      // and we don't want to block other API calls.
      val logger = BufferedLogger()
      val engine = DurableDAGEngine(
        stateStore = stateStore,
        logger = logger
      )

      val (result, log) = engine.run(wf, WorkflowContext(), id)

      // Store the log (synchronized)
      synchronized {
        runLogs = runLogs + (id -> log)
      }

      Right(
        WorkflowRunResponse(
          id = id,
          success = result.success,
          completedSteps = result.completedSteps,
          error = result.error,
          durationMs = log.totalMs
        )
      )
    }
  }

  // ── Get workflow status ───────────────────────────────────────

  def status(id: String): Either[String, WorkflowStatusResponse] =
    synchronized {
      workflows.get(id) match {
        case None           => Left(s"Workflow '$id' not found")
        case Some(workflow) =>
          // Check the state store for progress
          val (statusStr, completedSteps) = stateStore.load(id) match {
            case Right(Some(state)) =>
              val s = state.status match {
                case WorkflowStatus.Running     => "running"
                case WorkflowStatus.Completed   => "completed"
                case WorkflowStatus.Failed(err) => s"failed: $err"
                case WorkflowStatus.Paused      => "paused"
              }
              (s, state.completedSteps)
            case _ =>
              ("submitted", List.empty)
          }

          val totalSteps = workflow.nodes.size
          val progress =
            if (totalSteps > 0)
              (completedSteps.size * 100) / totalSteps
            else 0

          Right(
            WorkflowStatusResponse(
              id = id,
              name = workflow.name,
              status = statusStr,
              completedSteps = completedSteps,
              totalSteps = totalSteps,
              progressPercent = progress
            )
          )
      }
    }

  // ── Get execution log ─────────────────────────────────────────

  def log(id: String): Either[String, WorkflowLogResponse] = synchronized {
    runLogs.get(id) match {
      case None        => Left(s"No execution log for '$id'. Has it been run?")
      case Some(wfLog) =>
        Right(
          WorkflowLogResponse(
            id = id,
            workflowName = wfLog.workflowName,
            totalMs = wfLog.totalMs,
            succeeded = wfLog.succeeded,
            steps = wfLog.stepLogs.map { sl =>
              StepLogResponse(
                stepId = sl.stepId,
                stepName = sl.stepName,
                status = sl.status match {
                  case StepStatus.Success    => "success"
                  case StepStatus.Failed(e)  => s"failed: $e"
                  case StepStatus.Skipped(r) => s"skipped: $r"
                },
                durationMs = sl.durationMs,
                threadName = sl.threadName
              )
            }
          )
        )
    }
  }

  // ── List all workflows ────────────────────────────────────────

  def list(): WorkflowListResponse = synchronized {
    val submissions = workflows.map { case (id, wf) =>
      val statusStr = stateStore.load(id) match {
        case Right(Some(state)) =>
          state.status match {
            case WorkflowStatus.Running   => "running"
            case WorkflowStatus.Completed => "completed"
            case WorkflowStatus.Failed(_) => "failed"
            case WorkflowStatus.Paused    => "paused"
          }
        case _ => "submitted"
      }
      WorkflowSubmission(id, wf.name, wf.nodes.size, statusStr)
    }.toList

    WorkflowListResponse(submissions)
  }

  // ── Delete a workflow ─────────────────────────────────────────

  def delete(id: String): Either[String, String] = synchronized {
    if (workflows.contains(id)) {
      workflows = workflows - id
      runLogs = runLogs - id
      stateStore.clear(id)
      Right(s"Workflow '$id' deleted")
    } else {
      Left(s"Workflow '$id' not found")
    }
  }
}
