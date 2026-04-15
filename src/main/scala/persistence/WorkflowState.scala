package persistence

import upickle.default.*

// A snapshot of workflow progress at a point in time.
// This is what gets saved to disk after each level completes.

case class WorkflowState(
    workflowName: String,
    workflowId: String, // unique ID for this execution run
    completedSteps: List[String], // IDs of steps that finished successfully
    contextData: Map[String, String], // serialized context (String keys+values)
    status: WorkflowStatus,
    lastUpdated: Long = System.currentTimeMillis()
) derives ReadWriter

// What state is the overall workflow in?
enum WorkflowStatus derives ReadWriter:
  case Running
  case Completed
  case Failed(error: String)
  case Paused
