package api

import config.{DefaultRegistry, StepRegistry}
import persistence.FileStateStore

import scala.concurrent.ExecutionContext

given ExecutionContext = ExecutionContext.global

object WorkflowServerApp extends cask.Main {
  //                        ^^^^^^^^^^^^^^^
  //  Extending cask.Main gives us a proper main() that
  //  starts the server and blocks until shutdown.

  val registry: StepRegistry = DefaultRegistry.create()
  val stateStore = FileStateStore("checkpoints")
  val manager = WorkflowManager(registry, stateStore)

  override def allRoutes: Seq[cask.Routes] = Seq(
    WorkflowRoutes(manager)
  )

  override def host: String = "localhost"
  override def port: Int = 8080

  // cask.Main prints its own startup message, but let's add ours
  override def main(args: Array[String]): Unit = {
    println("=" * 60)
    println("  Workflow Engine Server")
    println("=" * 60)
    println(s"  Registry: ${registry.availableTypes.size} step types")
    println(
      s"  Step types: ${registry.availableTypes.toList.sorted.mkString(", ")}"
    )
    println()
    println("  Try:")
    println("    curl.exe http://localhost:8080/")
    println(
      "    curl.exe -X POST http://localhost:8080/workflows/test1 -d @workflows/order_processing.json"
    )
    println("    curl.exe -X POST http://localhost:8080/workflows/test1/run")
    println("    curl.exe http://localhost:8080/workflows/test1/status")
    println("=" * 60)
    println()

    // Call super.main — this starts the server and BLOCKS
    super.main(args)
  }
}
