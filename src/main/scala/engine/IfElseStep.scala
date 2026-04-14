package engine

// Convenience wrapper: a BranchStep with exactly two paths.
// Less boilerplate for the most common case.
//
// Instead of:
//   BranchStep("check", ctx => if (cond(ctx)) "yes" else "no",
//              Map("yes" -> stepA, "no" -> stepB))
//
// You write:
//   IfElseStep("check", cond, ifTrue = stepA, ifFalse = stepB)

class IfElseStep(
    val name: String,
    condition: WorkflowContext => Boolean,
    ifTrue: WorkflowStep,
    ifFalse: WorkflowStep
) extends WorkflowStep {

  def execute(ctx: WorkflowContext): Either[String, WorkflowContext] = {
    val result = condition(ctx)
    val chosen = if (result) ifTrue else ifFalse
    println(s"    Condition: $result → running ${chosen.name}")
    chosen.execute(ctx)
  }
}
