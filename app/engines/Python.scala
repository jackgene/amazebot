package engines

import java.io.ByteArrayInputStream
import java.lang.reflect.Method

import org.python.core.{BytecodeLoader, imp => PythonCompiler}

import scala.io.Source

/**
  * Support for running Python robot control programs.
  */
case object Python extends Language {
  val IndentLineExtractor = """(\s*)(\w.*)""".r
  val SkippedKeywords = Set(
    "#", "def", "for", "from", "import", "while"
  )

  def makeEntryPointMethod(source: String): Method = {
    val instrumentedSource: String =
      "from actors.SimulationRunActor import beforeRunningLine as line;" +
      Source.fromString(source).getLines.
        zipWithIndex.
        map {
          case (IndentLineExtractor(indent, ln), idx: Int) if !SkippedKeywords.exists(ln.startsWith) =>
            s"${indent}line(${idx + 1}); ${ln}"

          case (uninstrumentedLine, _) => uninstrumentedLine
        }.
        mkString("\n")
    val byteCode: Array[Byte] = PythonCompiler.compileSource(
      "robot", new ByteArrayInputStream(instrumentedSource.getBytes("UTF-8")), "robot$py"
    )

    BytecodeLoader.
      makeClass("robot$py", byteCode).
      getMethod("main", classOf[Array[String]])
  }
}
