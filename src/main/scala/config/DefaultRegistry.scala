package config

import steps.*

// Pre-built registry with all our known step types.
// To add a new step type to the engine, you add ONE line here.

object DefaultRegistry {

  def create(): StepRegistry = {
    StepRegistry()
      .register("FetchData") { cfg =>
        new FetchDataStep(cfg.getOrElse("url", "https://default.example.com"))
      }
      .register("TransformData") { _ =>
        new TransformDataStep()
      }
      .register("SaveResult") { _ =>
        new SaveResultStep()
      }
      .register("FetchOrder") { _ =>
        new FetchOrderStep()
      }
      .register("ValidateOrder") { _ =>
        new ValidateOrderStep()
      }
      .register("CheckInventory") { _ =>
        new CheckInventoryStep()
      }
      .register("ProcessOrder") { _ =>
        new ProcessOrderStep()
      }
      .register("NotifyCustomer") { _ =>
        new NotifyCustomerStep()
      }
      .register("Timed") { cfg =>
        // Configurable timed step — duration and output from JSON
        val ms = cfg.getOrElse("durationMs", "100").toLong
        val key = cfg.getOrElse("outputKey", "result")
        val value = cfg.getOrElse("outputValue", "done")
        new TimedStep(
          name = cfg.getOrElse("name", "TimedStep"),
          durationMs = ms,
          outputKey = engine.TypedKey[String](key),
          outputValue = value
        )
      }
  }
}
