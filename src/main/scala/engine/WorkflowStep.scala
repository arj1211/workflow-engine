package engine

import retry.RetryPolicy

trait WorkflowStep {
  def name: String
  def execute(ctx: WorkflowContext): Either[String, WorkflowContext]

  // Default: no retries. Steps can override this.
  def retryPolicy: Option[RetryPolicy] = None
}

case class WorkflowResult(
    success: Boolean,
    finalContext: WorkflowContext,
    error: Option[String] = None,
    completedSteps: List[String] = List.empty
)
