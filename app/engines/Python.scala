package engines

import java.io.ByteArrayInputStream
import java.lang.reflect.{InvocationTargetException, Method}

import exceptions.ExitTrappedException
import org.python.antlr._
import org.python.antlr.ast._
import org.python.antlr.base.expr
import org.python.antlr.runtime._
import org.python.core.{BytecodeLoader, Py, PyException, imp => PythonCompiler}
import org.python.util.PythonInterpreter
import play.api.Logger

import scala.collection.JavaConverters._
import scala.io.Source
import scala.util.{Failure, Random, Success, Try}

/**
  * Support for running Python robot control programs.
  */
case object Python extends Language {
  val IndentLineExtractor = """(\s*)(\w.*)""".r
  val ElseExtractor = """(else\s*:\s*|)(.*)""".r
  val ExitCodeExtractor = """SystemExit\\(([0-9]+),\\)""".r

  private case class InstrumentingVisitor(line: Int, source: String, instrumentFuncName: String)
      extends VisitorBase[String] {
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
      s"${instrumentFuncName}(${line});${source.slice(node.getCharStartIndex, node.getCharStopIndex)}"

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

  private def instrumentLine(num: Int, source: String, instrumentFuncName: String): Try[String] = Try {
    val parser = new BaseParser(new ANTLRStringStream(source), null, "UTF-8")

    parser.parseModule().accept(InstrumentingVisitor(num, source, instrumentFuncName))
  } recover {
    case _: Throwable => source
  }

  private def instrumentScript(source: String, instrumentFuncName: String): Try[String] =
    Source.fromString(source).getLines.
      zipWithIndex.
      map {
        case (IndentLineExtractor(indent, ElseExtractor(elseClause, sentence)), idx: Int) =>
          instrumentLine(idx + 1, sentence, instrumentFuncName).map { instrumentedSentence: String =>
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
      }.
      map(s"import warnings; warnings.filterwarnings('ignore'); from actors.SimulationRunActor import beforeRunningLine as ${instrumentFuncName};" + _)

  def makeRobotControlScript(source: String): () => Try[Unit] = {
    Logger.info("Compiling Python source to byte code")
    val instrumentFuncName: String = s"__ln${Random.nextInt(Int.MaxValue)}"
    val scriptToRun: String = instrumentScript(source, instrumentFuncName) match {
      case Success(instrumentedSource) => instrumentedSource

      case Failure(_) => source // Just pass the original and have it report error
    }

    () => Try[Unit] {
      new PythonInterpreter().exec(scriptToRun)
    }.recover {
      // Python exceptions with cause (typically from Java code?)
      case e: PyException if e.getCause != null =>
        throw e.getCause

      // Normal Python exceptions
      case e: PyException => e.value.toString match {
        case ExitCodeExtractor(status) =>
          throw ExitTrappedException(status.toInt)

        case "KeyboardInterrupt('interrupted sleep',)" | "java.lang.ClassCastException: java.lang.InterruptedException cannot be cast to java.lang.ClassNotFoundException" =>
          throw new InterruptedException

        case "java.lang.ThreadDeath" =>
          throw new ThreadDeath

        case _ =>
          new PyException(
            Py.None,
            e.toString.replaceAll(s"""${instrumentFuncName}\\([0-9]+\\);""", "")
          )
          throw e
      }

      case other: Throwable => throw other
    }
  }

  // Get Jython warmed up so that it stays within CPU thresholds for actual runs
  Logger.info("Warming up Jython")
  PythonInterpreter.initialize(null, null, Array("python", "-W", "ignore"))
  new PythonInterpreter().exec(
    """from time import sleep
      |from org.jointheleague.ecolban.rpirobot import SimpleIRobot
      |
      |robot = SimpleIRobot()
      |sleep(0.1)
      |print "Jython warm up complete..."
      |""".stripMargin
  )
}
