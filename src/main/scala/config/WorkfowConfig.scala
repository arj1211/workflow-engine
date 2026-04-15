package config

import upickle.default.*

// These case classes mirror the JSON structure.
//
// 'derives ReadWriter' is Scala 3 magic: it automatically generates
// the JSON serializer/deserializer for the case class.
// upickle looks at the field names and types and figures out
// how to convert to/from JSON. You write ZERO parsing code.

case class StepNodeConfig(
    id: String,
    stepType: String,
    dependsOn: List[String] = List.empty,
    config: Map[String, String] = Map.empty
) derives ReadWriter
//  ^^^^^^^^^^^^^^^^^^
//  This single line generates all the JSON parsing logic.
//  Without it, you'd have to write a custom parser for each class.

case class WorkflowConfig(
    name: String,
    nodes: List[StepNodeConfig]
) derives ReadWriter
