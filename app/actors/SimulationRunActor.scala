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
import models.{Maze, RobotPosition, RobotProgramStats, RobotState}
import org.jointheleague.ecolban.rpirobot.IRobotInterface
import play.api.libs.json.{JsValue, Json}

import scala.collection.immutable.Queue
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.math.{cos, sin}

object SimulationRunActor {
  // Incoming messages
  case class Drive(velocityMmS: Double, radiusMm: Option[Double]) {
    /**
      * Max newVelocityMmS is 500 per spec. However, given that each
      * wheel has a 500mm/s max, we know this is not possible for
      * radius < ∞. Limit to the actual max.
      *
      * Actual max is calculated by finding the angular velocity and
      * limiting the outer wheel speed to 500mm/s.
      */
    def adjustedVelocityMmS: Double = {
      radiusMm match {
        case None | Some(0.0) => velocityMmS

        case Some(r: Double) =>
          val rAbs: Double = math.abs(r)
          val vAbsMax: Double = 500.0 / (rAbs + WheelDisplacementMmPerRadian) * rAbs

          if (velocityMmS > 0) math.min(velocityMmS, vAbsMax)
          else math.max(velocityMmS, -vAbsMax)
      }
    }
  }
  case object ReadDistanceSensor
  case object ReadAngleSensor
  case class SonarPing(robotRelativeDirectionRad: Double)

  // Outgoing messages
  case class MoveRobot(position: RobotPosition)
  case class DistanceSensorValue(distanceMm: Option[Double])
  case class AngleSensorValue(angleRad: Option[Double])
  case class SonarPong(distanceMm: Double)
  case class ShowMessage(message: String)
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
    private val AllowedRuntimePerms = Set(
      "accessDeclaredMembers", "modifyThread", "setContextClassLoader",
      // Jython-specific
      "accessClassInPackage.sun.reflect",
      "accessClassInPackage.sun.text.resources",
      "accessClassInPackage.sun.util.resources",
      "createClassLoader",
      "getProtectionDomain"
    )
    private val AllowedReflectPerms = Set(
      // Jython-specific - they are all for Jython
      "newProxyInPackage.org.python.modules.posix",
      "suppressAccessChecks"
    )

    override def checkPermission(perm: Permission): Unit = {
      perm match {
        case _: PropertyPermission => // OK

        case runtime: RuntimePermission if AllowedRuntimePerms.contains(runtime.getName) => // OK

        case sysExit: RuntimePermission if sysExit.getName.startsWith("exitVM") =>
          throw new ExitTrappedException(perm.getName.substring(7).toInt)

        case reflect: ReflectPermission if AllowedReflectPerms.contains(reflect.getName) => // OK

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
        new Thread(r) {
          setPriority(Thread.MIN_PRIORITY)
          context.become(
            running(
              System.currentTimeMillis,
              RobotState(velocityMmS = 0, radiusMm = None),
              RobotPosition(
                topMm = maze.startPoint.topMm,
                leftMm = maze.startPoint.leftMm,
                orientationRad = maze.startOrientationRad
              ),
              Queue.empty,
              RobotProgramStats(this)
            )
          )

          /**
            * Heavy handed, but ensures that runaway programs get terminated successfully.
            */
          override def interrupt(): Unit = {
            super.interrupt()
            Thread.sleep(1)
            //noinspection ScalaDeprecation
            stop()
          }
        }
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
      case RobotState(0, _, _, _) => oldRobotPosition

      // Turning in place
      case RobotState(velocityMmS: Double, Some(0.0), _, _) =>
        oldRobotPosition.copy(
          orientationRad =
            oldRobotPosition.orientationRad +
            (velocityMmS * durationSecs) / WheelDisplacementMmPerRadian
        )

      // Moving in a straight line
      case RobotState(velocityMmS: Double, None, _, _) =>
        val displacementMm = velocityMmS * durationSecs
        val orientationRad = oldRobotPosition.orientationRad

        oldRobotPosition.copy(
          topMm = oldRobotPosition.topMm - (cos(orientationRad) * displacementMm),
          leftMm = oldRobotPosition.leftMm + (sin(orientationRad) * displacementMm)
        )

      // Moving in a curve
      case RobotState(velocityMmS: Double, Some(radiusMm: Double), _, _) =>
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

  private def distance(durationMillis: Long, robotState: RobotState): Double =
    robotState.radiusMm match {
      case Some(0.0) => 0.0
      case _ => robotState.velocityMmS * durationMillis / 1000.0
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

  private def initializing: Receive = PartialFunction.empty

  private def running(
      lastDriveChangeTimeMillis: Long, robotState: RobotState, robotPosition: RobotPosition,
      recentLines: Queue[ExecuteLine], robotProgram: RobotProgramStats):
      Receive = {
    case drive @ Drive(newVelocityMmS: Double, newRadiusMm: Option[Double])
        if drive.adjustedVelocityMmS != robotState.velocityMmS || newRadiusMm != robotState.radiusMm =>
      val newDriveChangeTimeMillis = System.currentTimeMillis()

      context.become(
        running(
          newDriveChangeTimeMillis,
          robotState.copy(
            velocityMmS = drive.adjustedVelocityMmS,
            radiusMm = newRadiusMm,
            distanceSensorState = robotState.distanceSensorState match {
              // Never read - nothing to track
              case noRead @ Left(None) => noRead

              // First drive changes since distance read
              case Left(Some(lastReadTimeMillis: Long)) =>
                Right(distance(newDriveChangeTimeMillis - lastReadTimeMillis, robotState))

              // There have been other drive changes since last read
              case Right(distToLastDriveChange: Double) =>
                Right(distToLastDriveChange + distance(newDriveChangeTimeMillis - lastDriveChangeTimeMillis, robotState))
            }
          ),
          moveRobot(lastDriveChangeTimeMillis, newDriveChangeTimeMillis, robotState, robotPosition),
          recentLines,
          robotProgram
        )
      )

    case ReadDistanceSensor =>
      val curReadTimeMillis: Long = System.currentTimeMillis()

      sender ! DistanceSensorValue(
        robotState.distanceSensorState match {
          // First read
          case Left(None) => None

          // There have been no drive changes since last read
          case Left(Some(lastReadTimeMillis: Long)) =>
            Some(distance(curReadTimeMillis - lastReadTimeMillis, robotState))

          // There have been drive changes since last read
          case Right(distToLastDriveChange: Double) =>
            Some(distToLastDriveChange + distance(curReadTimeMillis - lastDriveChangeTimeMillis, robotState))
        }
      )
      context.become(
        running(
          lastDriveChangeTimeMillis,
          robotState.copy(distanceSensorState = Left(Some(curReadTimeMillis))),
          robotPosition,
          recentLines,
          robotProgram
        )
      )

    case ReadAngleSensor =>
      val curOrientationRad: Double =
        moveRobot(lastDriveChangeTimeMillis, System.currentTimeMillis(), robotState, robotPosition).
        orientationRad

      sender ! AngleSensorValue(
        robotState.orientationRadOnSensorRead.map {
          _ - curOrientationRad
        }
      )
      context.become(
        running(
          lastDriveChangeTimeMillis,
          robotState.copy(orientationRadOnSensorRead = Some(curOrientationRad)),
          robotPosition,
          recentLines,
          robotProgram
        )
      )

    case SonarPing(robotRelativeDirectionRad: Double) =>
      val newTimeMillis = System.currentTimeMillis()
      val newRobotPosition: RobotPosition =
        moveRobot(lastDriveChangeTimeMillis, newTimeMillis, robotState, robotPosition)

      sender ! SonarPong(
        maze.distanceToClosestObstruction(newRobotPosition, robotRelativeDirectionRad).getOrElse(10000.0)
      )

    case execLine: ExecuteLine =>
      if (recentLines.isEmpty) {
        sendExecuteLine(execLine)
      }
      context.become(
        running(lastDriveChangeTimeMillis, robotState, robotPosition, recentLines.enqueue(execLine), robotProgram)
      )

    case UpdateView =>
      if (robotProgram.cpuTimePercent < 0.05 || robotProgram.runningTimeMillis < 1000) {
        val newTimeMillis = System.currentTimeMillis()
        val newRobotPosition: RobotPosition =
          moveRobot(lastDriveChangeTimeMillis, newTimeMillis, robotState, robotPosition)
        val obstructed: Boolean = maze.obstructionsInContact(newRobotPosition).nonEmpty
        lazy val finished: Boolean = maze.hasFinished(newRobotPosition)

        if (obstructed || finished) {
          val instrs: Seq[JsValue] = (obstructed, finished) match {
            case (true, _) =>
              val stepMillis: Int = (5000 / math.abs(robotState.velocityMmS)).toInt // step = time taken to travel 5mm/0.5px
              val adjNewRobotPosition: RobotPosition = Iterator.
                from(start = stepMillis, step = stepMillis).
                map { backupMillis: Int =>
                  moveRobot(lastDriveChangeTimeMillis, newTimeMillis - backupMillis, robotState, robotPosition)
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
      } else {
        webSocketOut ! Json.toJson(
          SimulationSessionActor.PrintToConsole(
            SimulationSessionActor.StdErr,
            "Simulation terminated for execessive CPU utilization."
          )
        )
        log.warning(
          f"Terminating simulation on execesive CPU utilization (${robotProgram.cpuTimePercent * 100}%,.2f%% for ${robotProgram.runningTimeMillis}ms)"
        )
        gracefulStop(recentLines)
      }

    case UpdateExecuteLine =>
      val (_, newRecentLines: Queue[ExecuteLine]) = recentLines.dequeue

      newRecentLines.dequeueOption match {
        case Some((execLine: ExecuteLine, _)) =>
          sendExecuteLine(execLine)

        case None =>
      }
      context.become(
        running(lastDriveChangeTimeMillis, robotState, robotPosition, newRecentLines, robotProgram)
      )

    case RobotProgramExited =>
      log.info("Simulation completed successfully.")
      if (robotState.velocityMmS != 0.0) self ! ExecuteLine(0)
      else {
        gracefulStop(recentLines.enqueue(ExecuteLine(0)))
        if (recentLines.isEmpty) self ! UpdateExecuteLine
      }

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

  override def receive = initializing

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
