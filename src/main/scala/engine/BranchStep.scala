package engine

// A step that looks at the context and decides WHICH sub-step to run.
//
// 'chooseBranch' is a function stored as a field. It takes a WorkflowContext
// and returns a String — the name of the branch to take.
//
// 'branches' maps branch names to the step that should run for that branch.
//
// Example:
//   chooseBranch = ctx => if (ctx.get(AmountKey).exists(_ > 1000)) "high" else "low"
//   branches = Map("high" -> managerApproval, "low" -> autoApprove)

class BranchStep(
    val name: String,
    chooseBranch: WorkflowContext => String,
    branches: Map[String, WorkflowStep],
    fallback: Option[WorkflowStep] = None
) extends WorkflowStep {

  def execute(ctx: WorkflowContext): Either[String, WorkflowContext] = {
    val branchName = chooseBranch(ctx)
    println(s"    Branch decision: '$branchName'")

    // Look up which step to run for this branch
    branches.get(branchName).orElse(fallback) match {
      case Some(step) =>
        println(s"    → Running branch step: ${step.name}")
        step.execute(ctx)
      case None =>
        Left(
          s"No branch found for '$branchName' and no fallback defined. " +
            s"Available branches: ${branches.keys.mkString(", ")}"
        )
    }
  }
}
