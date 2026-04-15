package api

import upickle.default.*

// These case classes define what the API sends back as JSON.
// Every response is one of these structured types — never a raw string.

case class ApiError(
    error: String,
    details: Option[String] = None
) derives ReadWriter

case class WorkflowSubmission(
    id: String,
    name: String,
    nodeCount: Int,
    status: String
) derives ReadWriter

case class WorkflowStatusResponse(
    id: String,
    name: String,
    status: String,
    completedSteps: List[String],
    totalSteps: Int,
    progressPercent: Int
) derives ReadWriter

case class WorkflowRunResponse(
    id: String,
    success: Boolean,
    completedSteps: List[String],
    error: Option[String],
    durationMs: Long
) derives ReadWriter

case class StepLogResponse(
    stepId: String,
    stepName: String,
    status: String,
    durationMs: Long,
    threadName: String
) derives ReadWriter

case class WorkflowLogResponse(
    id: String,
    workflowName: String,
    totalMs: Long,
    succeeded: Boolean,
    steps: List[StepLogResponse]
) derives ReadWriter

case class WorkflowListResponse(
    workflows: List[WorkflowSubmission]
) derives ReadWriter
