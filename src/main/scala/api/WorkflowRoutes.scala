package api

import upickle.default.*

class WorkflowRoutes(manager: WorkflowManager) extends cask.Routes {
  //                                                    ^^^^^^^^^^^
  //  cask.Routes (not MainRoutes) — just defines routes,
  //  doesn't try to be the entry point

  @cask.post("/workflows/:id")
  def submitWorkflow(
      id: String,
      request: cask.Request
  ): cask.Response[String] = {
    val json = new String(request.readAllBytes())
    manager.submit(id, json) match {
      case Right(submission) =>
        jsonResponse(200, write(submission))
      case Left(error) =>
        jsonResponse(400, write(ApiError(error)))
    }
  }

  @cask.post("/workflows/:id/run")
  def runWorkflow(id: String): cask.Response[String] = {
    manager.run(id) match {
      case Right(result) =>
        val code = if (result.success) 200 else 500
        jsonResponse(code, write(result))
      case Left(error) =>
        jsonResponse(404, write(ApiError(error)))
    }
  }

  @cask.get("/workflows/:id/status")
  def getStatus(id: String): cask.Response[String] = {
    manager.status(id) match {
      case Right(status) =>
        jsonResponse(200, write(status))
      case Left(error) =>
        jsonResponse(404, write(ApiError(error)))
    }
  }

  @cask.get("/workflows/:id/log")
  def getLog(id: String): cask.Response[String] = {
    manager.log(id) match {
      case Right(log) =>
        jsonResponse(200, write(log))
      case Left(error) =>
        jsonResponse(404, write(ApiError(error)))
    }
  }

  @cask.get("/workflows")
  def listWorkflows(): cask.Response[String] = {
    jsonResponse(200, write(manager.list()))
  }

  @cask.delete("/workflows/:id")
  def deleteWorkflow(id: String): cask.Response[String] = {
    manager.delete(id) match {
      case Right(msg) =>
        jsonResponse(200, write(Map("message" -> msg)))
      case Left(error) =>
        jsonResponse(404, write(ApiError(error)))
    }
  }

  @cask.get("/")
  def root(): cask.Response[String] = {
    val welcome = ujson.Obj(
      "service" -> "Workflow Engine",
      "version" -> "0.1.0",
      "endpoints" -> ujson.Arr(
        "POST   /workflows/:id       - submit workflow JSON",
        "POST   /workflows/:id/run   - run a workflow",
        "GET    /workflows/:id/status - check status",
        "GET    /workflows/:id/log   - get execution log",
        "GET    /workflows            - list all workflows",
        "DELETE /workflows/:id       - delete a workflow"
      )
    )
    jsonResponse(200, ujson.write(welcome, indent = 2))
  }

  private def jsonResponse(
      statusCode: Int,
      body: String
  ): cask.Response[String] = {
    cask.Response(
      data = body,
      statusCode = statusCode,
      headers = Seq("Content-Type" -> "application/json")
    )
  }

  initialize()
}
