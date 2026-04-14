package retry

case class RetryPolicy(
    maxRetries: Int = 3,
    initialDelayMs: Long = 100,
    backoffMultiplier: Double = 2.0
)
