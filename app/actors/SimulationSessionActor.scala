package actors

import java.io.{ByteArrayOutputStream, OutputStream, PrintStream}
import java.lang.reflect.Method

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import models.{Maze, Point, RobotPosition}
import org.codehaus.commons.compiler.{CompileException, CompilerFactoryFactory}
import play.api.libs.json.Json

/**
  * Maintains state for a single simulation session (page load).
  *
  * @author Jack Leow
  * @since August 2017
  */
object SimulationSessionActor {
  // Outgoing messages
  case class DrawMaze(finish: Point, walls: Set[Maze.Wall])
  case class InitializeRobot(position: RobotPosition)
  case class PrintToConsole(messageType: ConsoleMessageType, message: String)

  sealed abstract class ConsoleMessageType
  case object StdOut extends ConsoleMessageType
  case object StdErr extends ConsoleMessageType

  def props(webSocketOut: ActorRef, maze: Maze): Props = {
    Props(classOf[SimulationSessionActor], webSocketOut, maze)
  }

  private[actors] case class MessageSendingOutputStream(webSocketOut: ActorRef, msg: String => PrintToConsole)
      extends OutputStream {
    private val buf = new ByteArrayOutputStream()

    override def flush(): Unit = {
      import models.ViewUpdateInstructions._

      webSocketOut ! Json.toJson(msg(buf.toString))
      buf.reset()
    }

    override def write(b: Int): Unit = buf.write(b)

    override def write(b: Array[Byte]): Unit = buf.write(b)

    override def write(b: Array[Byte], off: Int, len: Int): Unit = buf.write(b, off, len)
  }
}
class SimulationSessionActor(webSocketOut: ActorRef, maze: Maze) extends Actor with ActorLogging {
  import SimulationSessionActor._
  import models.ViewUpdateInstructions._

  private val PackageNameExtractor =
    """(?s).*package\s+((?:[A-Za-z][A-Za-z0-9]+)(?:\.[A-Za-z][A-Za-z0-9]+)*).*""".r
  private val ClassNameExtractor =
    """(?s).*public\s+class\s+([A-Za-z][A-Za-z0-9]+).*""".r

  private def receive(currentRunOpt: Option[ActorRef], run: Int): Receive = {
    case javaSource: String =>
      // Stop previous run
      currentRunOpt.foreach(context.stop)

      // Compile simulation source
      val PackageNameExtractor(packageName: String) = javaSource
      val ClassNameExtractor(className: String) = javaSource
      val compiler = CompilerFactoryFactory.getDefaultCompilerFactory.newSimpleCompiler()
      try {
        compiler.cook(javaSource)

        // Re-initialize robot position
        webSocketOut ! Json.toJson(
          InitializeRobot(
            RobotPosition(
              topMm = Maze.theMaze.startPoint.topMm,
              leftMm = Maze.theMaze.startPoint.leftMm,
              orientationRad = Maze.theMaze.startOrientationRad
            )
          )
        )

        // Run simulation
        val classLoader = compiler.getClassLoader
        val controllerClass = classLoader.loadClass(s"${packageName}.${className}")
        val main: Method = controllerClass.getMethod("main", classOf[Array[String]])
        val nextRun = run + 1
        context.become(
          receive(
            Some(
              context.actorOf(
                SimulationRunActor.props(webSocketOut, maze, main),
                s"run-${nextRun}"
              )
            ),
            nextRun
          )
        )
      } catch {
        case e: CompileException =>
          e.printStackTrace(
            new PrintStream(
              SimulationSessionActor.MessageSendingOutputStream(
                webSocketOut,
                SimulationSessionActor.PrintToConsole(SimulationSessionActor.StdErr, _)
              ),
              true
            )
          )
      }
  }

  override def receive: Receive = receive(None, 0)

  webSocketOut ! Json.toJson(
    DrawMaze(maze.finish, maze.walls)
  )
  webSocketOut ! Json.toJson(
    InitializeRobot(
      RobotPosition(
        topMm = maze.startPoint.topMm,
        leftMm = maze.startPoint.leftMm,
        orientationRad = maze.startOrientationRad
      )
    )
  )
}
