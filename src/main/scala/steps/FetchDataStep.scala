package steps

import engine.{ContextKeys, WorkflowContext, WorkflowStep}

class FetchDataStep(url: String) extends WorkflowStep {
  val name = "FetchData"

  def execute(ctx: WorkflowContext): Either[String, WorkflowContext] = {
    println(s"    Fetching from $url...")
    val fakeData = """{"users": [{"name": "Alice"}, {"name": "Bob"}]}"""
    Right(ctx.set(ContextKeys.RawData, fakeData))
  }
}
