package logging

import java.time.{Instant, Duration}

// The outcome of a step — a sealed enum so the compiler
// knows all possibilities.
enum StepStatus:
  case Success
  case Failed(error: String)
  case Skipped(reason: String)

// Everything we want to know about one step's execution.
case class StepLog(
    stepId: String,
    stepName: String,
    status: StepStatus,
    startTime: Instant,
    endTime: Instant,
    threadName: String = ""
) {
  // Computed property — not stored, calculated on access
  def duration: Duration = Duration.between(startTime, endTime)
  def durationMs: Long = duration.toMillis

  def succeeded: Boolean = status == StepStatus.Success

  override def toString: String = {
    val statusStr = status match {
      case StepStatus.Success    => "✓"
      case StepStatus.Failed(e)  => s"✗ $e"
      case StepStatus.Skipped(r) => s"⊘ $r"
    }
    val thread = if (threadName.nonEmpty) s" [$threadName]" else ""
    s"$statusStr $stepName ($stepId) ${durationMs}ms$thread"
  }
}
