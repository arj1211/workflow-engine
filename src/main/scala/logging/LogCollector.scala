package logging

import java.time.Instant

// Collects step logs during workflow execution.
//
// 'synchronized' means: only one thread can execute this block
// at a time. Without it, two parallel steps calling addLog
// simultaneously could corrupt the list.
//
// This is a MUTABLE class — it accumulates state over time.
// That's appropriate here: it's a builder/accumulator pattern,
// created fresh for each workflow run and not shared beyond that.

class LogCollector(val workflowName: String) {
  private var logs = List.empty[StepLog]
  private val _startTime: Instant = Instant.now()

  def addLog(log: StepLog): Unit = synchronized {
    logs = logs :+ log
  }

  def stepCount: Int = synchronized { logs.size }

  def build(): WorkflowLog = synchronized {
    WorkflowLog(
      workflowName = workflowName,
      startTime = _startTime,
      endTime = Instant.now(),
      stepLogs = logs
    )
  }
}
