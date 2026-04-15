package persistence

// Stores everything in a Map in memory.
// Perfect for tests — fast, no file system, no cleanup needed.

class InMemoryStateStore extends StateStore {
  private var store = Map.empty[String, WorkflowState]

  def save(state: WorkflowState): Either[String, Unit] = {
    store = store + (state.workflowId -> state)
    Right(())
  }

  def load(workflowId: String): Either[String, Option[WorkflowState]] = {
    Right(store.get(workflowId))
  }

  def clear(workflowId: String): Either[String, Unit] = {
    store = store - workflowId
    Right(())
  }

  def listAll(): Either[String, List[String]] = {
    Right(store.keys.toList)
  }

  // Extra helper for tests
  def isEmpty: Boolean = store.isEmpty
}
