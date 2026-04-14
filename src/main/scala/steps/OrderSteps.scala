package steps

import engine.{ContextKeys, WorkflowContext, WorkflowStep}

// A step that sets up a fake order for testing
class CreateOrderStep(total: Double, region: String) extends WorkflowStep {
  val name = "CreateOrder"
  def execute(ctx: WorkflowContext): Either[String, WorkflowContext] = {
    println(s"    Created order: total=$$$total, region=$region")
    Right(
      ctx
        .set(ContextKeys.OrderTotal, total)
        .set(ContextKeys.OrderRegion, region)
    )
  }
}

class ManagerApprovalStep extends WorkflowStep {
  val name = "ManagerApproval"
  def execute(ctx: WorkflowContext): Either[String, WorkflowContext] = {
    println("    📋 Sending to manager for approval...")
    println("    ✓ Manager approved (simulated)")
    Right(ctx.set(ContextKeys.ApprovalStatus, "manager_approved"))
  }
}

class AutoApproveStep extends WorkflowStep {
  val name = "AutoApprove"
  def execute(ctx: WorkflowContext): Either[String, WorkflowContext] = {
    println("    ⚡ Auto-approved (under threshold)")
    Right(ctx.set(ContextKeys.ApprovalStatus, "auto_approved"))
  }
}

class DomesticShippingStep extends WorkflowStep {
  val name = "DomesticShipping"
  def execute(ctx: WorkflowContext): Either[String, WorkflowContext] = {
    println("    🚚 Standard domestic shipping selected")
    Right(ctx.set(ContextKeys.ShippingMethod, "domestic_ground"))
  }
}

class InternationalShippingStep extends WorkflowStep {
  val name = "InternationalShipping"
  def execute(ctx: WorkflowContext): Either[String, WorkflowContext] = {
    println("    ✈️ International express shipping selected")
    Right(ctx.set(ContextKeys.ShippingMethod, "international_express"))
  }
}

class FinalizeOrderStep extends WorkflowStep {
  val name = "FinalizeOrder"
  def execute(ctx: WorkflowContext): Either[String, WorkflowContext] = {
    val approval = ctx.get(ContextKeys.ApprovalStatus).getOrElse("unknown")
    val shipping = ctx.get(ContextKeys.ShippingMethod).getOrElse("unknown")
    println(s"    ✓ Order finalized (approval=$approval, shipping=$shipping)")
    Right(ctx.set(ContextKeys.Saved, true))
  }
}
