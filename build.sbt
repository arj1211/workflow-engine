ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.8.3"

lazy val root = (project in file("."))
  .settings(
    name := "SimpleWorkflowEngine",
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % "1.3.0" % Test,
      "com.lihaoyi" %% "upickle" % "4.4.3"
    )
  )
