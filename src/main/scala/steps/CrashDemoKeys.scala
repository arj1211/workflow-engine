package steps

import engine.TypedKey

object CrashDemoKeys {
  val Fetched: TypedKey[String] = TypedKey[String]("fetched")
  val Validated: TypedKey[String] = TypedKey[String]("validated")
  val Inventoried: TypedKey[String] = TypedKey[String]("inventoried")
  val Processed: TypedKey[String] = TypedKey[String]("processed")
  val Notified: TypedKey[String] = TypedKey[String]("notified")
}
