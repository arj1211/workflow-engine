package retry

enum RetryResult[+T]:
  case Success(value: T, attempts: Int)
  case Failure(
      errors: List[String]
  )
