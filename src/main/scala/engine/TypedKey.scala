package engine

// A key that knows what type of value it holds.
// Think of it like a labeled jar: the label says both the NAME
// and what TYPE of thing is inside.
//
// TypedKey[String]("rawData")     → can only store/retrieve Strings
// TypedKey[Int]("retryCount")     → can only store/retrieve Ints
// TypedKey[Boolean]("saved")      → can only store/retrieve Booleans

class TypedKey[T](val name: String) {
  override def toString: String = s"Key($name)"
}
