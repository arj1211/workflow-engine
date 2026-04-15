package persistence

import engine.WorkflowContext

// Converts WorkflowContext to/from a serializable format.
//
// The problem: WorkflowContext holds Map[String, Any], but
// JSON doesn't understand 'Any'. We need to convert to strings.
//
// Strategy: store each value as a string with a type prefix
// so we can reconstruct the original type on load.
//
//   42        → "int:42"
//   3.14      → "double:3.14"
//   true      → "boolean:true"
//   "hello"   → "string:hello"

object ContextSerializer {

  def serialize(ctx: WorkflowContext): Map[String, String] = {
    ctx.allData.map { case (key, value) =>
      val serialized = value match {
        case s: String  => s"string:$s"
        case i: Int     => s"int:$i"
        case l: Long    => s"long:$l"
        case d: Double  => s"double:$d"
        case b: Boolean => s"boolean:$b"
        case other      => s"string:${other.toString}"
        //                  ^^^^^^^^
        //  Fallback: anything we don't recognize gets
        //  stored as a string. Not perfect, but safe.
      }
      key -> serialized
    }
  }

  def deserialize(data: Map[String, String]): WorkflowContext = {
    val restored = data.map { case (key, serialized) =>
      val value: Any = serialized.split(":", 2) match {
        //               ^^^^^^^^^^^^^^^^^^^^
        //  Split on first colon only. "string:hello:world"
        //  becomes Array("string", "hello:world")
        //  The '2' limits to 2 parts so colons in values are preserved.
        case Array("string", v)  => v
        case Array("int", v)     => v.toInt
        case Array("long", v)    => v.toLong
        case Array("double", v)  => v.toDouble
        case Array("boolean", v) => v.toBoolean
        case _                   => serialized // unrecognized, keep as-is
      }
      key -> value
    }
    WorkflowContext(restored)
  }
}
