package engines

import java.io.ByteArrayInputStream
import java.lang.reflect.Method

import org.python.antlr._
import org.python.antlr.ast._
import org.python.antlr.base.expr
import org.python.antlr.runtime._
import org.python.core.{BytecodeLoader, imp => PythonCompiler}
import org.python.util.PythonInterpreter

import scala.io.Source
import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

/**
  * Support for running Python robot control programs.
  */
case object Python extends Language {
  val IndentLineExtractor = """(\s*)(\w.*)""".r
  val ElseExtractor = """(else\s*:\s*|)(.*)""".r

  private case class InstrumentingVisitor(line: Int, source: String) extends VisitorBase[String] {
    private def visitAll(nodes: java.util.List[_ <: PythonTree], sep: String): String =
      nodes.asScala.map(_.accept(this)).mkString(";")

    // Lines requiring special handling
    private def visitexpr(node: expr): String =
      node.getToken.getInputStream.substring(node.getCharStartIndex, node.getCharStopIndex)
    override def visitFor(node: For): String =
      s"${node.getText} ${node.getInternalTarget.getText} in ${visitexpr(node.getInternalIter)} ${visitAll(node.getInternalBody, ";")}"
    override def visitFunctionDef(node: FunctionDef): String = {
      // TODO there's probably a better way to do this
      val args: String = node.getInternalArgs.getText match {
        case "(" => ""
        case args: String => args
      }

      s"${node.getText} ${node.getInternalName}(${args}): ${visitAll(node.getInternalBody, ";")}"
    }
    override def visitIf(node: If): String =
      s"${node.getText} ${visitexpr(node.getInternalTest)} ${visitAll(node.getInternalBody, ";")}"
    override def visitWhile(node: While): String =
      s"${node.getText} ${visitexpr(node.getInternalTest)} ${visitAll(node.getInternalBody, ";")}"

    // Lines not to be instrumented
    override def visitImport(node: Import): String = source
    override def visitImportFrom(node: ImportFrom): String = source

    // Default - Lines are instrumented
    override def unhandled_node(node: PythonTree): String =
      s"line(${line});${source.slice(node.getCharStartIndex, node.getCharStopIndex)}"

    override def visitModule(node: Module): String = {
      if (node.getChildCount == 0) ""
      else {
        node.getInternalBody.asScala.
          map(_.accept(this)).
          mkString(";")
      }
    }

    override def traverse(node: PythonTree): Unit = {
      node.traverse(this)
    }
  }

  private def instrumentLine(num: Int, source: String): Try[String] = Try {
    val parser = new BaseParser(new ANTLRStringStream(source), null, "UTF-8")

    parser.parseModule().accept(InstrumentingVisitor(num, source))
  } recover {
    case _: Throwable => source
  }

  def instrumentScript(source: String): Try[String] =
    Source.fromString(source).getLines.
      zipWithIndex.
      map {
        case (IndentLineExtractor(indent, ElseExtractor(elseClause, sentence)), idx: Int) =>
          instrumentLine(idx + 1, sentence).map { instrumentedSentence: String =>
            s"${indent}${elseClause}${instrumentedSentence}"
          }

        case (uninstrumentedLine, _) =>
          Success(uninstrumentedLine)
      }.
      reduce { (firstLineTry: Try[String], secondLineTry: Try[String]) =>
        for {
          firstLine: String <- firstLineTry
          secondLine: String <- secondLineTry
        } yield firstLine + "\n" + secondLine
      }

  def makeEntryPointMethod(source: String): Method = {
    val scriptToRun: String = instrumentScript(source) match {
      case Success(instrumentedSource) =>
        "from actors.SimulationRunActor import beforeRunningLine as line;" + instrumentedSource

      case Failure(_) => source // Just pass the original and have it report error
    }
    val byteCode: Array[Byte] = PythonCompiler.compileSource(
      "script", new ByteArrayInputStream(scriptToRun.getBytes("UTF-8")), "script$py"
    )

    BytecodeLoader.
      makeClass("script$py", byteCode).
      getMethod("main", classOf[Array[String]])
  }

  // Get Jython warmed up so that it stays within CPU thresholds for the actual run
  new PythonInterpreter().exec(
    """from org.jointheleague.ecolban.rpirobot import SimpleIRobot
      |robot = SimpleIRobot()
      |robot.getAngle()""".stripMargin)
}
