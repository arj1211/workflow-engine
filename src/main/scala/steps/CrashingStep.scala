package steps

import engine.{WorkflowContext, WorkflowStep, TypedKey}

// A step that tracks how many times it's been called across
// the entire JVM lifetime. Used to simulate crashes:
//
// First run:  steps 1,2 succeed, step 3 "crashes" (returns Left)
// Second run: engine resumes, step 3 succeeds this time
//
// In real life, the crash would be a JVM shutdown. For demo
// purposes we simulate it by failing on the first call.

object CrashTracker {
  // Tracks which steps have "crashed" already.
  // After crashing once, they succeed next time.
  // This simulates: first run crashes, restart succeeds.
  private var crashed = Set.empty[String]

  def shouldCrash(stepId: String): Boolean = synchronized {
    if (crashed.contains(stepId)) {
      false // already crashed before, succeed this time
    } else {
      crashed += stepId
      true // first time, simulate crash
    }
  }

  def reset(): Unit = synchronized { crashed = Set.empty }
}

class CrashingStep(
    val name: String,
    outputKey: TypedKey[String],
    outputValue: String
) extends WorkflowStep {

  def execute(ctx: WorkflowContext): Either[String, WorkflowContext] = {
    if (CrashTracker.shouldCrash(name)) {
      Left(s"$name crashed! (simulated failure)")
    } else {
      println(s"      $name recovered and completed successfully")
      Right(ctx.set(outputKey, outputValue))
    }
  }
}
