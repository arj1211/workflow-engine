package logging

// What does the engine do when something happens?
// This trait lets you plug in different behaviors.

trait Logger {
  def info(message: String): Unit
  def warn(message: String): Unit
  def error(message: String): Unit
  def debug(message: String): Unit
}

// Print everything to console
class ConsoleLogger extends Logger {
  def info(message: String): Unit = println(s"  [INFO]  $message")
  def warn(message: String): Unit = println(s"  [WARN]  $message")
  def error(message: String): Unit = println(s"  [ERROR] $message")
  def debug(message: String): Unit = println(s"  [DEBUG] $message")
}

// Swallow everything — useful for tests or benchmarks
class SilentLogger extends Logger {
  def info(message: String): Unit = ()
  def warn(message: String): Unit = ()
  def error(message: String): Unit = ()
  def debug(message: String): Unit = ()
}

// Collect into a buffer — useful for testing "did the engine log X?"
class BufferedLogger extends Logger {
  private var _messages = List.empty[(String, String)] // (level, message)

  def info(message: String): Unit = synchronized {
    _messages :+= ("INFO", message)
  }
  def warn(message: String): Unit = synchronized {
    _messages :+= ("WARN", message)
  }
  def error(message: String): Unit = synchronized {
    _messages :+= ("ERROR", message)
  }
  def debug(message: String): Unit = synchronized {
    _messages :+= ("DEBUG", message)
  }

  def messages: List[(String, String)] = synchronized { _messages }
  def clear(): Unit = synchronized { _messages = List.empty }
}
