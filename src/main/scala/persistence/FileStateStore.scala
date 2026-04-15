package persistence

import upickle.default.*

import java.nio.file.{Files, Path, Paths}
import scala.util.{Try, Success, Failure}

// Stores each workflow's checkpoint as a JSON file on disk.
// File name: {directory}/{workflowId}.json
//
// This is simple and good enough for a single-machine engine.
// For distributed engines, you'd use a database instead.

class FileStateStore(directory: String) extends StateStore {

  // Ensure the directory exists
  private val dir = Paths.get(directory)
  if (!Files.exists(dir)) {
    Files.createDirectories(dir)
  }

  private def pathFor(workflowId: String): Path =
    dir.resolve(s"$workflowId.json")

  def save(state: WorkflowState): Either[String, Unit] = {
    Try {
      val json = write(state, indent = 2)
      //         ^^^^^^^^^^^^
      //  upickle's write() serializes a case class to JSON.
      //  indent = 2 makes it human-readable (pretty-printed).
      Files.writeString(pathFor(state.workflowId), json)
    } match {
      case Success(_)  => Right(())
      case Failure(ex) => Left(s"Failed to save state: ${ex.getMessage}")
    }
  }

  def load(workflowId: String): Either[String, Option[WorkflowState]] = {
    val path = pathFor(workflowId)
    if (!Files.exists(path)) {
      Right(None) // no checkpoint yet — that's fine, not an error
    } else {
      Try {
        val json = Files.readString(path)
        read[WorkflowState](json)
        //   ^^^^^^^^^^^^^^^^
        //  upickle's read() deserializes JSON to a case class.
      } match {
        case Success(state) => Right(Some(state))
        case Failure(ex)    => Left(s"Failed to load state: ${ex.getMessage}")
      }
    }
  }

  def clear(workflowId: String): Either[String, Unit] = {
    Try {
      val path = pathFor(workflowId)
      if (Files.exists(path)) Files.delete(path)
    } match {
      case Success(_)  => Right(())
      case Failure(ex) => Left(s"Failed to clear state: ${ex.getMessage}")
    }
  }

  def listAll(): Either[String, List[String]] = {
    Try {
      import scala.jdk.CollectionConverters.*
      //     ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
      //  Java's Files.list returns a Java Stream.
      //  This import adds .asScala to convert to Scala collections.
      Files
        .list(dir)
        .iterator()
        .asScala
        .toList
        .filter(_.toString.endsWith(".json"))
        .map(_.getFileName.toString.stripSuffix(".json"))
    } match {
      case Success(ids) => Right(ids)
      case Failure(ex)  => Left(s"Failed to list states: ${ex.getMessage}")
    }
  }
}
