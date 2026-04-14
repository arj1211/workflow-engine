package engine

// All context keys in one place.
// Adding a new one is one line. The type is enforced everywhere.
object ContextKeys {
  val RawData: TypedKey[String] = TypedKey[String]("rawData")
  val TransformedData: TypedKey[String] = TypedKey[String]("transformedData")
  val Saved: TypedKey[Boolean] = TypedKey[Boolean]("saved")
  val Url: TypedKey[String] = TypedKey[String]("url")
  val OrderTotal: TypedKey[Double] = TypedKey[Double]("orderTotal")
  val ApprovalStatus: TypedKey[String] = TypedKey[String]("approvalStatus")
  val OrderRegion: TypedKey[String] = TypedKey[String]("orderRegion")
  val ShippingMethod: TypedKey[String] = TypedKey[String]("shippingMethod")
}
