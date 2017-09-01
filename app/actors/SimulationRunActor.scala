package actors

import java.io.{FilePermission, PrintStream}
import java.lang.reflect.{InvocationTargetException, Method, ReflectPermission}
import java.security.Permission
import java.util.PropertyPermission
import java.util.concurrent.locks.LockSupport
import java.util.concurrent.{Executors, RejectedExecutionException, ThreadFactory}

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Status}
import akka.pattern.pipe
import exceptions.ExitTrappedException
import io.PerThreadPrintStream
import models.{Maze, RobotPosition, RobotState}
import org.jointheleague.ecolban.rpirobot.IRobotInterface
import play.api.libs.json.{JsValue, Json}

import scala.collection.immutable.Queue
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.math.{cos, sin}

object SimulationRunActor {
  // Incoming messages
  case class Drive(velocityMmS: Double, radiusMm: Option[Double])
  case class Ping(robotRelativeDirectionRad: Double)

  // Outgoing messages
  case class MoveRobot(position: RobotPosition)
  case class ShowMessage(message: String)
  case class Pong(distanceMm: Double)
  case class ExecuteLine(line: Int)

  // Internal messages
  private case object UpdateView
  private case object UpdateExecuteLine
  private case object RobotProgramExited

  def props(webSocketOut: ActorRef, maze: Maze, main: Method): Props = {
    Props(classOf[SimulationRunActor], webSocketOut, maze: Maze, main)
  }

  val simulationRunHolder = new ThreadLocal[ActorRef]

  // Stricter security manager to limit what simulation can do
  private val securityManager = new SecurityManager() {
    override def checkPermission(perm: Permission): Unit = {
      val AllowedRuntimePermissions = Set(
        "accessDeclaredMembers", "modifyThread", "setContextClassLoader",
        // Jython-specific
        "accessClassInPackage.sun.reflect",
        "accessClassInPackage.sun.text.resources",
        "accessClassInPackage.sun.util.resources",
        "createClassLoader"
      )
      perm match {
        case _: PropertyPermission => // OK

        case runtime: RuntimePermission if AllowedRuntimePermissions.contains(runtime.getName) => // OK

        case sysExit: RuntimePermission if sysExit.getName.startsWith("exitVM") =>
          throw new ExitTrappedException(perm.getName.substring(7).toInt)

        // Jython-specific
        case reflect: ReflectPermission if reflect.getName == "suppressAccessChecks" => // OK

        case libRead: FilePermission if libRead.getName.contains("/lib/") && libRead.getActions == "read" => // OK

        case _ =>
          throw new SecurityException(perm.toString)
      }
    }
  }
  private val originalSecurityManager = System.getSecurityManager
  private val securityManagerHolder = new ThreadLocal[SecurityManager] {
    override val initialValue = originalSecurityManager
  }
  System.setSecurityManager(
    new SecurityManager() {
      override def checkPermission(perm: Permission) {
        val simulationSecurityManager: SecurityManager =
          SimulationRunActor.securityManagerHolder.get

        if (simulationSecurityManager != null) simulationSecurityManager.checkPermission(perm)
      }
    }
  )

  private val WheelDisplacementMmPerRadian = IRobotInterface.WHEEL_DISTANCE / 2.0

  def beforeRunningLine(line: Int): Unit = {
    LockSupport.parkNanos(100000L)
    if (Thread.interrupted()) System.exit(143)

    simulationRunHolder.get ! ExecuteLine(line)
  }
}
class SimulationRunActor(webSocketOut: ActorRef, maze: Maze, main: Method) extends Actor with ActorLogging {
  import SimulationRunActor._
  import context.dispatcher
  import models.ViewUpdateInstructions._

  val executorService = Executors.newSingleThreadExecutor(
    new ThreadFactory {
      override def newThread(r: Runnable): Thread = {
        val t = new Thread(r)
        t.setPriority(Thread.MIN_PRIORITY)

        t
      }
    }
  )

  val viewUpdateScheduler = context.system.scheduler.schedule(0.millis, 200.millis, self, UpdateView)

  private def moveRobot(
      oldTimeMillis: Long, newTimeMillis: Long,
      robotState: RobotState, oldRobotPosition: RobotPosition):
      RobotPosition = {
    val durationSecs = (newTimeMillis - oldTimeMillis) / 1000.0

    robotState match {
      // Not moving
      case RobotState(0, _) => oldRobotPosition

      // Turning in place
      case RobotState(velocityMmS: Double, Some(0.0)) =>
        oldRobotPosition.copy(
          orientationRad =
            oldRobotPosition.orientationRad +
            (velocityMmS * durationSecs) / WheelDisplacementMmPerRadian
        )

      // Moving in a straight line
      case RobotState(velocityMmS: Double, None) =>
        val displacementMm = velocityMmS * durationSecs
        val orientationRad = oldRobotPosition.orientationRad

        oldRobotPosition.copy(
          topMm = oldRobotPosition.topMm - (cos(orientationRad) * displacementMm),
          leftMm = oldRobotPosition.leftMm + (sin(orientationRad) * displacementMm)
        )

      // Moving in a curve
      case RobotState(velocityMmS: Double, Some(radiusMm: Double)) =>
        val displacementMm = velocityMmS * durationSecs
        val orientationDeltaRads = displacementMm / -radiusMm.toDouble
        val orientationRads = oldRobotPosition.orientationRad
        val newOrientationRads = orientationRads + orientationDeltaRads
        val axisTop = oldRobotPosition.topMm - (sin(orientationRads) * radiusMm)
        val axisLeft = oldRobotPosition.leftMm - (cos(orientationRads) * radiusMm)

        oldRobotPosition.copy(
          topMm = axisTop + sin(newOrientationRads) * radiusMm,
          leftMm = axisLeft + cos(newOrientationRads) * radiusMm,
          orientationRad = oldRobotPosition.orientationRad + orientationDeltaRads
        )
    }
  }

  private def sendExecuteLine(execLine: ExecuteLine): Unit = {
    webSocketOut ! Json.toJson(execLine)
    context.system.scheduler.scheduleOnce(10.millis, self, UpdateExecuteLine)
  }

  private def gracefulStop(recentLines: Queue[ExecuteLine]): Unit = {
    if (recentLines.isEmpty) context.stop(self)
    else {
      postStop() // Perform clean up already
      context.become(stopping(recentLines))
    }
  }

  private def running(
      timeMillis: Long, robotState: RobotState, robotPosition: RobotPosition, recentLines: Queue[ExecuteLine]):
      Receive = {
    case Drive(newVelocityMmS: Double, newRadiusMm: Option[Double])
        if newVelocityMmS != robotState.velocityMmS || newRadiusMm != robotState.radiusMm =>
      val newTimeMillis = System.currentTimeMillis()

      context.become(
        running(
          newTimeMillis,
          RobotState(newVelocityMmS, newRadiusMm),
          moveRobot(timeMillis, newTimeMillis, robotState, robotPosition),
          recentLines
        )
      )

    case Ping(robotRelativeDirectionRad: Double) =>
      val newTimeMillis = System.currentTimeMillis()
      val newRobotPosition: RobotPosition =
        moveRobot(timeMillis, newTimeMillis, robotState, robotPosition)

      sender ! Pong(
        maze.distanceToClosestObstruction(newRobotPosition, robotRelativeDirectionRad).getOrElse(10000.0)
      )

    case execLine: ExecuteLine =>
      if (recentLines.size > 1000) {
        webSocketOut ! Json.toJson(
          SimulationSessionActor.PrintToConsole(
            SimulationSessionActor.StdErr,
            "Simulation terminated for execessive CPU utilization."
          )
        )
        log.warning("Terminating simulation on execesive CPU utilization")
        gracefulStop(recentLines)
      } else {
        if (recentLines.isEmpty) {
          sendExecuteLine(execLine)
        }
        context.become(
          running(timeMillis, robotState, robotPosition, recentLines.enqueue(execLine))
        )
      }

    case UpdateView =>
      val newTimeMillis = System.currentTimeMillis()
      val newRobotPosition: RobotPosition =
        moveRobot(timeMillis, newTimeMillis, robotState, robotPosition)

      val obstructed: Boolean = maze.obstructionsInContact(newRobotPosition).nonEmpty
      lazy val finished: Boolean = maze.hasFinished(newRobotPosition)
      if (obstructed || finished) {
        val instrs: Seq[JsValue] = (obstructed, finished) match {
          case (true, _) =>
            val stepMillis: Int = (5000 / math.abs(robotState.velocityMmS)).toInt // step = time taken to travel 5mm/0.5px
            val adjNewRobotPosition: RobotPosition = Iterator.
              from(start = stepMillis, step = stepMillis).
              map { backupMillis: Int =>
                moveRobot(timeMillis, newTimeMillis - backupMillis, robotState, robotPosition)
              }.
              find { maze.obstructionsInContact(_).isEmpty }.
              get
            Seq(
              Json.toJson(MoveRobot(adjNewRobotPosition)),
              if (maze.hasFinished(adjNewRobotPosition)) Json.toJson(ShowMessage("You have won!"))
              else Json.toJson(ShowMessage("You have hit a wall!"))
            )

          case (false, true) =>
            Seq(
              Json.toJson(MoveRobot(newRobotPosition)),
              Json.toJson(ShowMessage("You have won!"))
            )

          case (false, false) => Seq()
            // This will never happen. Shame on the Scala compiler for not being able to infer this.
        }
        instrs.foreach { webSocketOut ! _}
        gracefulStop(recentLines)
      } else {
        webSocketOut ! Json.toJson(MoveRobot(newRobotPosition))
      }

    case UpdateExecuteLine =>
      val (_, newRecentLines: Queue[ExecuteLine]) = recentLines.dequeue
      newRecentLines.dequeueOption match {
        case Some((execLine: ExecuteLine, _)) =>
          sendExecuteLine(execLine)

        case None =>
      }
      context.become(
        running(timeMillis, robotState, robotPosition, newRecentLines)
      )

    case RobotProgramExited =>
      log.info("Simulation completed successfully.")
      self ! ExecuteLine(0)
      if (robotState.velocityMmS == 0.0) gracefulStop(recentLines)

    case Status.Failure(cause: Throwable) =>
      log.error(cause, "Simulation terminated abnormally.")
      gracefulStop(recentLines)
  }

  private def stopping(recentLines: Queue[ExecuteLine]): Receive = {
    case execLine: ExecuteLine => // No-op, this is usually from malicious code?

    case UpdateExecuteLine =>
      val (_, newRecentLines: Queue[ExecuteLine]) = recentLines.dequeue
      newRecentLines.dequeueOption match {
        case Some((execLine: ExecuteLine, _)) =>
          sendExecuteLine(execLine)
          context.become(stopping(newRecentLines))

        case None => context.stop(self)
      }
  }

  override def receive = running(
    System.currentTimeMillis,
    RobotState(velocityMmS = 0, radiusMm = None),
    RobotPosition(
      topMm = maze.startPoint.topMm,
      leftMm = maze.startPoint.leftMm,
      orientationRad = maze.startOrientationRad
    ),
    Queue.empty
  )

  override def postStop(): Unit = {
    viewUpdateScheduler.cancel()
    executorService.shutdownNow()
  }

  // Start simulation
  {
    implicit val ec = ExecutionContext.fromExecutorService(
      executorService,
      {
        case _: RejectedExecutionException => // Swallow
        case other: Throwable => other.printStackTrace()
      }
    )

    Future {
      simulationRunHolder.set(self)
      securityManagerHolder.set(securityManager)
      PerThreadPrintStream.redirectStdOut(
        new PrintStream(
          SimulationSessionActor.MessageSendingOutputStream(
            webSocketOut,
            SimulationSessionActor.PrintToConsole(SimulationSessionActor.StdOut, _)
          ),
          true
        )
      )
      PerThreadPrintStream.redirectStdErr(
        new PrintStream(
          SimulationSessionActor.MessageSendingOutputStream(
            webSocketOut,
            SimulationSessionActor.PrintToConsole(SimulationSessionActor.StdErr, _)
          ),
          true
        )
      )
      main.invoke(null, Array[String]())
      RobotProgramExited
    } recover {
      case e: InvocationTargetException =>
        e.getCause match {
          case exitTrapped @ ExitTrappedException(status: Int) =>
            if (status != 0)
              System.err.println(s"Simulation completed with a non-zero exit code of ${status}")
            RobotProgramExited

          case other: Throwable =>
            other.printStackTrace()
            throw other
        }
    }
  } pipeTo context.self
}
