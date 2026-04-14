package engine

// A step that runs a list of sub-steps in order.
// This lets you use multiple steps anywhere a single step is expected.
//
// This is the Composite Pattern: a group of things that behaves
// like a single thing of the same type.

class SequenceStep(
    val name: String,
    subSteps: List[WorkflowStep]
) extends WorkflowStep {

  def execute(ctx: WorkflowContext): Either[String, WorkflowContext] = {

    // foldLeft again! Walk through sub-steps, threading the context.
    // But this time we use Either directly — if any step returns Left,
    // we stop (because flatMap on Left short-circuits).

    subSteps.foldLeft[Either[String, WorkflowContext]](Right(ctx)) {
      case (Left(err), _) =>
        // Previous step failed — skip remaining
        Left(err)
      case (Right(currentCtx), step) =>
        println(s"      ▶ (sub) ${step.name}")
        step.execute(currentCtx)
    }
  }
}
