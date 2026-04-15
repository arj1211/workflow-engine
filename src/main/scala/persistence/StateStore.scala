package persistence

// The interface for persisting workflow state.
// Implementations decide WHERE the data goes (file, database, memory, etc.)

trait StateStore {
  // Save a checkpoint. Overwrites any previous state for this workflowId.
  def save(state: WorkflowState): Either[String, Unit]

  // Load the most recent checkpoint for a workflow run.
  def load(workflowId: String): Either[String, Option[WorkflowState]]
  //                                            ^^^^^^
  //  Option because there might not be a checkpoint yet
  //  (first run, or after clear()).
  //  Right(None) = success, but nothing saved yet
  //  Right(Some(state)) = success, here's the checkpoint
  //  Left(error) = something went wrong reading

  // Delete checkpoint (after successful completion).
  def clear(workflowId: String): Either[String, Unit]

  // List all saved workflow IDs.
  def listAll(): Either[String, List[String]]
}
