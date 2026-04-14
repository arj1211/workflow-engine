package steps

import engine.{ContextKeys, WorkflowContext, WorkflowStep}

class SaveResultStep extends WorkflowStep {
  val name = "SaveResult"

  def execute(ctx: WorkflowContext): Either[String, WorkflowContext] = {
    ctx.get[String](ContextKeys.TransformedData) match {
      case Some(data) =>
        println(s"    Saving: ${data.take(50)}...")
        Right(ctx.set(ContextKeys.Saved, true))
      case None => Left("No transformedData found in context!")
    }
  }
}
