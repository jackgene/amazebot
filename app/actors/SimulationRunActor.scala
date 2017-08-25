package actors

import java.io.PrintStream
import java.lang.reflect.Method
import java.util.concurrent.{Executors, ThreadFactory}

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern.pipe
import io.PerThreadPrintStream
import models.{Maze, RobotPosition, RobotState}
import org.jointheleague.ecolban.rpirobot.IRobotInterface
import play.api.libs.json.{JsValue, Json}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.math.{cos, sin}

object SimulationRunActor {
  // Incoming messages
  case object UpdateView
  case object RobotProgramExited
  case class Drive(velocityMmS: Double, radiusMm: Option[Double])
  case class Ping(robotRelativeDirectionRad: Double)

  // Outgoing messages
  case class MoveRobot(position: RobotPosition)
  case class ShowMessage(message: String)
  case class Pong(distanceMm: Double)

  def props(webSocketOut: ActorRef, maze: Maze, main: Method): Props = {
    Props(classOf[SimulationRunActor], webSocketOut, maze: Maze, main)
  }

  val simulationRunHolder = new ThreadLocal[ActorRef]

  private val WheelDisplacementMmPerRadian = IRobotInterface.WHEEL_DISTANCE / 2.0
}
class SimulationRunActor(webSocketOut: ActorRef, maze: Maze, main: Method) extends Actor with ActorLogging {
  import SimulationRunActor._
  import context.dispatcher
  import models.ViewUpdateInstructions._

  val executorService = Executors.newSingleThreadExecutor(
    new ThreadFactory {
      override def newThread(r: Runnable): Thread = {
        new Thread(r) {
          {
            setPriority(Thread.MIN_PRIORITY)
          }

          //noinspection ScalaDeprecation
          // A bit heavy handed, but makes sure any malicious/poorly written
          // code in main is stopped, that may not be stopped by the default
          // interrupt implementation.
          override def interrupt(): Unit = stop()
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

  private def receive(timeMillis: Long, robotState: RobotState, robotPosition: RobotPosition): Receive = {
    case Drive(newVelocityMmS: Double, newRadiusMm: Option[Double])
        if newVelocityMmS != robotState.velocityMmS || newRadiusMm != robotState.radiusMm =>
      val newTimeMillis = System.currentTimeMillis()

      context.become(
        receive(
          newTimeMillis,
          RobotState(newVelocityMmS, newRadiusMm),
          moveRobot(timeMillis, newTimeMillis, robotState, robotPosition)
        )
      )

    case Ping(robotRelativeDirectionRad: Double) =>
      sender ! Pong(10000.0)

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
        context.stop(self)
      } else {
        webSocketOut ! Json.toJson(MoveRobot(newRobotPosition))
      }

    case RobotProgramExited =>
      if (robotState.velocityMmS == 0.0) context.stop(self)
  }

  override def receive = receive(
    System.currentTimeMillis,
    RobotState(velocityMmS = 0, radiusMm = None),
    RobotPosition(
      topMm = maze.startPoint.topMm,
      leftMm = maze.startPoint.leftMm,
      orientationRad = maze.startOrientationRad
    )
  )

  override def postStop(): Unit = {
    viewUpdateScheduler.cancel()
    executorService.shutdownNow()
  }

  // Start simulation
  {
    implicit val ec = ExecutionContext.fromExecutorService(executorService)

    Future {
      simulationRunHolder.set(self)
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
    } recoverWith {
      case t: Throwable =>
        println("Exception in user program")
        t.printStackTrace()
        Future.failed(t)
    }
  } pipeTo context.self
}
