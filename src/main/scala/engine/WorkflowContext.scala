package engine

case class WorkflowContext(data: Map[String, Any] = Map.empty) {
  def get[T](key: TypedKey[T]): Option[T] =
    data.get(key.name).map(x => x.asInstanceOf[T])

  def set[T](key: TypedKey[T], value: T): WorkflowContext =
    this.copy(data = data + (key.name -> value))

  def contains[T](key: TypedKey[T]): Boolean = data.contains(key.name)

  def keys: Set[String] = data.keySet

  def allData: Map[String, Any] = data

  override def toString: String =
    s"WorkflowContext(${data.map { case (k, v) => s"$k -> $v" }.mkString(", ")})"
}
