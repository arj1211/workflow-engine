package steps

import engine.{ContextKeys, TypedKey, WorkflowContext, WorkflowStep}

// Additional context keys for the DAG demo
object DAGKeys {
  val ValidationResult: TypedKey[String] = TypedKey[String]("validationResult")
  val InventoryStatus: TypedKey[String] = TypedKey[String]("inventoryStatus")
  val ProcessingId: TypedKey[String] = TypedKey[String]("processingId")
  val NotificationSent: TypedKey[Boolean] =
    TypedKey[Boolean]("notificationSent")
}

class FetchOrderStep extends WorkflowStep {
  val name = "FetchOrder"
  def execute(ctx: WorkflowContext): Either[String, WorkflowContext] = {
    println("      Fetching order from database...")
    Thread.sleep(100) // simulate work
    Right(
      ctx
        .set(ContextKeys.OrderTotal, 1500.0)
        .set(ContextKeys.OrderRegion, "EU")
    )
  }
}

class ValidateOrderStep extends WorkflowStep {
  val name = "ValidateOrder"
  def execute(ctx: WorkflowContext): Either[String, WorkflowContext] = {
    println("      Validating order details...")
    Thread.sleep(150) // simulate work
    Right(ctx.set(DAGKeys.ValidationResult, "valid"))
  }
}

class CheckInventoryStep extends WorkflowStep {
  val name = "CheckInventory"
  def execute(ctx: WorkflowContext): Either[String, WorkflowContext] = {
    println("      Checking warehouse inventory...")
    Thread.sleep(200) // simulate work
    Right(ctx.set(DAGKeys.InventoryStatus, "in_stock"))
  }
}

class ProcessOrderStep extends WorkflowStep {
  val name = "ProcessOrder"
  def execute(ctx: WorkflowContext): Either[String, WorkflowContext] = {
    // This step uses results from BOTH validation and inventory
    val validation = ctx.get(DAGKeys.ValidationResult).getOrElse("unknown")
    val inventory = ctx.get(DAGKeys.InventoryStatus).getOrElse("unknown")
    println(
      s"      Processing order (validation=$validation, inventory=$inventory)..."
    )
    Thread.sleep(100)
    Right(ctx.set(DAGKeys.ProcessingId, "ORD-2024-001"))
  }
}

class NotifyCustomerStep extends WorkflowStep {
  val name = "NotifyCustomer"
  def execute(ctx: WorkflowContext): Either[String, WorkflowContext] = {
    val orderId = ctx.get(DAGKeys.ProcessingId).getOrElse("unknown")
    println(s"      📧 Sending confirmation email for order $orderId...")
    Right(ctx.set(DAGKeys.NotificationSent, true))
  }
}
