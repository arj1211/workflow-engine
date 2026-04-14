package steps

import engine.{ContextKeys, WorkflowContext, WorkflowStep}

class TransformDataStep extends WorkflowStep {
  val name = "TransformData"

  def execute(ctx: WorkflowContext): Either[String, WorkflowContext] = {
    ctx.get[String](ContextKeys.RawData) match {
      case Some(raw) =>
        val transformed = raw.toUpperCase
        Right(ctx.set(ContextKeys.TransformedData, transformed))
      case None => Left("No rawData found in context!")
    }
  }
}
