package engine

import retry.{RetryPolicy, Retrier, RetryResult}

class WorkflowEngine {

  def run(steps: List[WorkflowStep], ctx: WorkflowContext): WorkflowResult = {
    val result =
      steps.foldLeft[(WorkflowContext, List[String], Option[String])](
        (ctx, List.empty, None)
      ) {
        case ((currentCtx, completed, Some(err)), _) =>
          (currentCtx, completed, Some(err))

        case ((currentCtx, completed, None), step) =>
          println(s"  ▶ Running step: ${step.name}")

          // Check if this step wants retries
          val retryResult = step.retryPolicy match {
            case Some(policy) =>
              // Wrap the step execution in a retrier
              Retrier.retry(policy) { () => step.execute(currentCtx) }
            case None =>
              // No retry — run once, wrap result to match the same type
              step.execute(currentCtx) match {
                case Right(newCtx) => RetryResult.Success(newCtx, attempts = 1)
                case Left(err)     => RetryResult.Failure(List(err))
              }
          }

          retryResult match {
            case RetryResult.Success(newCtx, attempts) =>
              val suffix =
                if (attempts > 1) s" (took $attempts attempts)" else ""
              println(s"  ✓ ${step.name} succeeded$suffix")
              (newCtx, completed :+ step.name, None)
            case RetryResult.Failure(errors) =>
              println(s"  ✗ ${step.name} failed permanently:")
              errors.foreach(e => println(s"      $e"))
              (currentCtx, completed, Some(errors.last))
          }
      }

    val (finalCtx, completed, error) = result
    WorkflowResult(
      success = error.isEmpty,
      finalContext = finalCtx,
      error = error,
      completedSteps = completed
    )
  }
}
