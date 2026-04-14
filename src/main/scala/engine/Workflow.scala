package engine

// A single node in the workflow graph.
//
// 'id' is how other nodes refer to this one in their dependencies.
// 'step' is the actual WorkflowStep to execute.
// 'dependencies' is the set of node IDs that must complete BEFORE this one runs.

case class StepNode(
    id: String,
    step: WorkflowStep,
    dependencies: Set[String] = Set.empty
)

// The complete workflow definition: a name and a graph of nodes.

case class Workflow(
    name: String,
    nodes: Map[String, StepNode]
) {

  // ── Validation ──────────────────────────────────────────────
  // Call this before running. Returns Left(error) if the workflow
  // is malformed, Right(()) if it's valid.

  def validate(): Either[String, Unit] = {
    // Check 1: every dependency references a node that actually exists
    val allDependencies = nodes.values.flatMap(_.dependencies).toSet
    val missing = allDependencies -- nodes.keySet
    //            ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    //  Set difference: "all dependencies MINUS all node IDs"
    //  If anything is left over, those are dangling references.

    if (missing.nonEmpty)
      Left(s"Unknown dependencies: ${missing.mkString(", ")}")
    else if (hasCycle)
      Left("Workflow contains a cycle — cannot determine execution order!")
    else
      Right(())
  }

  // ── Cycle Detection ─────────────────────────────────────────
  // A cycle means "A depends on B, B depends on C, C depends on A"
  // — impossible to run. We detect this with depth-first search.
  //
  // Uses 'var' for mutable state. This is fine for local algorithm
  // state that doesn't escape the function. The rule of thumb:
  //   - var for LOCAL algorithm state = fine
  //   - var for SHARED mutable state across threads = dangerous

  private def hasCycle: Boolean = {
    var visited = Set.empty[String] // nodes we've fully explored
    var inStack = Set.empty[String] // nodes in our current DFS path

    def dfs(nodeId: String): Boolean = {
      if (inStack.contains(nodeId)) return true // found a cycle!
      if (visited.contains(nodeId))
        return false // already explored, no cycle here

      inStack += nodeId

      // Check all dependencies of this node
      val foundCycle = nodes(nodeId).dependencies.exists(depId => dfs(depId))

      inStack -= nodeId
      visited += nodeId
      foundCycle
    }

    // Run DFS from every node (some might not be reachable from others)
    nodes.keys.exists(dfs)
  }

  // ── Topological Sort (Kahn's Algorithm) ─────────────────────
  //
  // Returns a List[Set[String]] — a list of "levels".
  // Each level is a set of node IDs that can run simultaneously.
  //
  // Example for our order workflow:
  //   List(
  //     Set("fetch"),                          // level 0
  //     Set("validate", "checkInventory"),     // level 1 (parallel!)
  //     Set("process"),                        // level 2
  //     Set("notify")                          // level 3
  //   )
  //
  // The algorithm:
  //   1. Find all nodes with 0 unmet dependencies → that's level 0
  //   2. "Remove" those nodes from the graph
  //   3. Recalculate: find nodes whose dependencies are now all removed
  //   4. That's level 1. Repeat until no nodes remain.

  def executionLevels(): List[Set[String]] = {
    // require() is a precondition check. If the condition is false,
    // it throws an IllegalArgumentException. Use it for things that
    // should NEVER happen if the caller uses your API correctly.
    require(validate().isRight, "Cannot compute levels for invalid workflow")

    // Track how many unmet dependencies each node has
    var inDegree: Map[String, Int] = nodes.map { case (id, node) =>
      id -> node.dependencies.size
    }
    var remaining = nodes.keySet // nodes not yet assigned to a level
    var levels = List.empty[Set[String]]

    while (remaining.nonEmpty) {
      // Find all nodes with zero unmet dependencies
      val ready: Set[String] = remaining.filter(id => inDegree(id) == 0)

      if (ready.isEmpty) {
        // This shouldn't happen if validate() passed — but be safe
        throw new RuntimeException(
          s"Stuck! Remaining nodes: ${remaining.mkString(", ")}. Possible cycle."
        )
      }

      levels = levels :+ ready

      // "Remove" the ready nodes:
      // For every remaining node, reduce its in-degree by the number
      // of its dependencies that were in the 'ready' set.
      remaining = remaining -- ready
      inDegree = remaining.map { id =>
        val node = nodes(id)
        val satisfiedDeps = node.dependencies.count(ready.contains)
        id -> (inDegree(id) - satisfiedDeps)
      }.toMap
    }

    levels
  }
}
