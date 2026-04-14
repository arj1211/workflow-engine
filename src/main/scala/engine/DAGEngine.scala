package engine

class DAGEngine {

  def run(workflow: Workflow, ctx: WorkflowContext): WorkflowResult = {

    // Validate the workflow first
    workflow.validate() match {
      case Left(err) =>
        return WorkflowResult(
          success = false,
          finalContext = ctx,
          error = Some(err)
        )
      case Right(_) => // valid, continue
    }

    val levels = workflow.executionLevels()

    // Print the execution plan
    val planStr = levels
      .map { level =>
        if (level.size == 1) level.head
        else s"[${level.mkString(" | ")}]" // brackets = parallel group
      }
      .mkString(" → ")
    println(s"  Execution plan: $planStr")

    // Run level by level
    var currentCtx = ctx
    var completed = List.empty[String]

    for ((level, levelIndex) <- levels.zipWithIndex) {
      //                        ^^^^^^^^^^^^^^^^
      //  .zipWithIndex turns List("a", "b") into List(("a", 0), ("b", 1))
      //  Useful when you need both the element and its position.

      val parallelHint = if (level.size > 1) " ⚡parallel" else ""
      println(s"\n  ── Level $levelIndex$parallelHint ──")

      // Run all steps in this level
      // (For now, this is sequential. Phase 6 makes it truly parallel.)
      val stepResults: List[(String, Either[String, WorkflowContext])] =
        level.toList.map { nodeId =>
          val node = workflow.nodes(nodeId)
          println(s"    ▶ ${node.step.name} (id: $nodeId)")
          val result = node.step.execute(currentCtx)
          (nodeId, result)
        }

      // Process results: merge successes into context, fail fast on errors
      for ((nodeId, result) <- stepResults) {
        result match {
          case Right(newCtx) =>
            // Merge the new data into our running context
            // This is important: each step might add different keys,
            // and we want ALL of them available to downstream steps.
            currentCtx = WorkflowContext(currentCtx.allData ++ newCtx.allData)
            completed = completed :+ nodeId
            println(s"    ✓ $nodeId succeeded")

          case Left(err) =>
            println(s"    ✗ $nodeId failed: $err")
            return WorkflowResult(
              success = false,
              finalContext = currentCtx,
              error = Some(s"$nodeId failed: $err"),
              completedSteps = completed
            )
        }
      }
    }

    WorkflowResult(
      success = true,
      finalContext = currentCtx,
      completedSteps = completed
    )
  }
}
