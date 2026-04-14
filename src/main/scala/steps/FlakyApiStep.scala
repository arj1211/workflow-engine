package steps

import engine.{WorkflowContext, WorkflowStep, ContextKeys}
import retry.RetryPolicy

class FlakyApiStep extends WorkflowStep {
  val name = "FlakyApiCall"

  // This step WANTS retries — 3 retries, starting at 200ms
  override def retryPolicy: Option[RetryPolicy] =
    Some(
      RetryPolicy(maxRetries = 3, initialDelayMs = 200, backoffMultiplier = 2.0)
    )

  // Track attempts using a mutable counter (just for demonstration!)
  private var attemptCount = 0

  def execute(ctx: WorkflowContext): Either[String, WorkflowContext] = {
    attemptCount += 1
    println(s"    Calling external API (attempt $attemptCount)...")

    // Simulate: fails the first 2 times, succeeds on the 3rd
    if (attemptCount < 3) {
      Left(s"Connection timeout (attempt $attemptCount)")
    } else {
      val apiResult = """{"status": "ok", "data": [1, 2, 3]}"""
      Right(ctx.set(ContextKeys.RawData, apiResult))
    }
  }
}
