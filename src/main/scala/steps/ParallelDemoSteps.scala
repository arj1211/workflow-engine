package steps

import engine.{WorkflowContext, WorkflowStep, TypedKey}

// A step that just sleeps for a specified time.
// Useful for demonstrating parallel vs sequential timing.

class TimedStep(
    val name: String,
    durationMs: Long,
    outputKey: TypedKey[String],
    outputValue: String
) extends WorkflowStep {
  def execute(ctx: WorkflowContext): Either[String, WorkflowContext] = {
    println(s"      Working for ${durationMs}ms...")
    Thread.sleep(durationMs)
    Right(ctx.set(outputKey, outputValue))
  }
}

// Keys for the parallel demo
object ParallelDemoKeys {
  val StepA: TypedKey[String] = TypedKey[String]("stepA")
  val StepB: TypedKey[String] = TypedKey[String]("stepB")
  val StepC: TypedKey[String] = TypedKey[String]("stepC")
  val StepD: TypedKey[String] = TypedKey[String]("stepD")
  val Final: TypedKey[String] = TypedKey[String]("final")
}
