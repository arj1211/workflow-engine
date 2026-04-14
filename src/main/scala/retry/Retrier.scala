package retry

import scala.annotation.tailrec

object Retrier {

  // Retry any operation that returns Either[String, T].
  // 'operation' is a FUNCTION passed as an argument (higher-order function).
  //
  // Type signature reads:
  //   "Give me a function from () to Either[String, T], and a RetryPolicy,
  //    and I'll give you back a RetryResult[T]"

  def retry[T](
      policy: RetryPolicy
  )(operation: () => Either[String, T]): RetryResult[T] = {

    // Inner recursive function that tracks state between attempts.
    //
    // @tailrec tells the compiler: "this recursion MUST be optimizable
    // into a loop. If it can't be (e.g. the recursive call isn't in
    // tail position), give me a compile error."
    // This prevents stack overflow on many retries.

    @tailrec
    def attempt(
        remainingRetries: Int,
        currentDelay: Long,
        errorsSoFar: List[String]
    ): RetryResult[T] = {

      val totalAttempts = policy.maxRetries - remainingRetries + 1

      operation() match {
        case Right(value) =>
          // Success! Report how many attempts it took
          RetryResult.Success(value, totalAttempts)

        case Left(error) =>
          val allErrors = errorsSoFar :+ s"Attempt $totalAttempts: $error"

          if (remainingRetries <= 0) {
            // No retries left — give up
            RetryResult.Failure(allErrors)
          } else {
            // Wait, then try again
            println(
              s"    ⟳ Retry in ${currentDelay}ms ($remainingRetries retries left)..."
            )
            Thread.sleep(currentDelay)
            val nextDelay = (currentDelay * policy.backoffMultiplier).toLong
            attempt(remainingRetries - 1, nextDelay, allErrors)
            // ↑ This recursive call is in "tail position" (it's the very
            //   last thing the function does), so @tailrec works.
          }
      }
    }

    attempt(policy.maxRetries, policy.initialDelayMs, List.empty)
  }
}
