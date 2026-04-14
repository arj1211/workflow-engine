package logging

import java.time.{Instant, Duration}

case class WorkflowLog(
    workflowName: String,
    startTime: Instant,
    endTime: Instant,
    stepLogs: List[StepLog]
) {
  def totalDuration: Duration = Duration.between(startTime, endTime)
  def totalMs: Long = totalDuration.toMillis

  def succeeded: Boolean = stepLogs.forall(_.succeeded)
  //                                ^^^^^^
  //  .forall(predicate) returns true if EVERY element matches.
  //  "Did all steps succeed?"

  def failedSteps: List[StepLog] = stepLogs.filter(!_.succeeded)
  //                                        ^^^^^^
  //  .filter(predicate) keeps only elements that match.

  def slowestStep: Option[StepLog] =
    if (stepLogs.isEmpty) None
    else Some(stepLogs.maxBy(_.durationMs))
  //                   ^^^^^
  //  .maxBy(f) returns the element with the largest f(element).

  def summary: String = {
    val status = if (succeeded) "✓ SUCCEEDED" else "✗ FAILED"
    val lines = List(
      s"┌─── $workflowName $status (${totalMs}ms) ───",
      "│"
    ) ++
      stepLogs.map(log => s"│  $log") ++
      List(
        "│",
        s"│  Steps: ${stepLogs.size} total, ${failedSteps.size} failed",
        slowestStep
          .map(s => s"│  Slowest: ${s.stepName} (${s.durationMs}ms)")
          .getOrElse(""),
        s"└─── ${totalMs}ms total ───"
      ).filter(_.nonEmpty)

    lines.mkString("\n")
  }
}
