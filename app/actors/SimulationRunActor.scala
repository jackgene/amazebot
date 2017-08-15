package actors

import java.lang.reflect.Method
import java.util.concurrent.{Executors, ThreadFactory}

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import models.{RobotPosition, RobotState}
import org.jointheleague.ecolban.rpirobot.{IRobotInterface, SimpleIRobot}
import play.api.libs.functional.syntax._
import play.api.libs.json.{JsPath, Json, Writes}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.math.{cos, sin}
import scala.util.{Failure, Success}

object SimulationRunActor {
  // Incoming messages
  case object UpdateView
  case object RobotProgramExited
  case class Drive(velocityMmS: Double, radiusMm: Option[Double])

  // Outgoing messages
  case class MoveRobot(position: RobotPosition)

  // JSON writes
  implicit val moveRobotWrites: Writes[MoveRobot] = (
    (JsPath \ "c").write[String] and
    (JsPath \ "t").write[Double] and
    (JsPath \ "l").write[Double] and
    (JsPath \ "o").write[Double]
  )(
    {
      case MoveRobot(position: RobotPosition) =>
        ("mv", position.topMm, position.leftMm, position.orientationRads)
    }: MoveRobot => (String,Double,Double,Double)
  )
  def props(webSocketOut: ActorRef, main: Method): Props = {
    Props(classOf[SimulationRunActor], webSocketOut, main)
  }

  private val WheelDisplacementMmPerRadian = IRobotInterface.WHEEL_DISTANCE / 2.0
}
class SimulationRunActor(webSocketOut: ActorRef, main: Method) extends Actor with ActorLogging {
  import SimulationRunActor._
  import context.dispatcher

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
          override def interrupt() = stop()
        }
      }
    }
  )

  {
    implicit val ec = ExecutionContext.fromExecutorService(executorService)

    Future {
      SimpleIRobot.simulationRunHolder.set(self)
      main.invoke(null, Array[String]())
      RobotProgramExited
    } recoverWith {
      case t: Throwable =>
        println("Exception in user program")
        t.printStackTrace()
        Future.failed(t)
    }
  } onComplete {
    case Success(exited) => println(":)")
    case Failure(t: Throwable) => println(":(")
  }

  context.system.scheduler.schedule(250.millis, 250.millis, self, UpdateView)

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
          orientationRads =
            oldRobotPosition.orientationRads +
            (velocityMmS * durationSecs) / WheelDisplacementMmPerRadian
        )

      // Moving in a straight line
      case RobotState(velocityMmS: Double, None) =>
        val displacementMm = velocityMmS * durationSecs
        val orientationRad = oldRobotPosition.orientationRads

        oldRobotPosition.copy(
          topMm = oldRobotPosition.topMm - (cos(orientationRad) * displacementMm),
          leftMm = oldRobotPosition.leftMm + (sin(orientationRad) * displacementMm)
        )

      // Moving in a curve
      case RobotState(velocityMmS: Double, Some(radiusMm: Double)) =>
        val displacementMm = velocityMmS * durationSecs
        val orientationDeltaRads = displacementMm / -radiusMm.toDouble
        val orientationRads = oldRobotPosition.orientationRads
        val newOrientationRads = orientationRads + orientationDeltaRads
        val axisTop = oldRobotPosition.topMm - (sin(orientationRads) * radiusMm)
        val axisLeft = oldRobotPosition.leftMm - (cos(orientationRads) * radiusMm)

        oldRobotPosition.copy(
          topMm = axisTop + sin(newOrientationRads) * radiusMm,
          leftMm = axisLeft + cos(newOrientationRads) * radiusMm,
          orientationRads = oldRobotPosition.orientationRads + orientationDeltaRads
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

    case UpdateView =>
      val newTimeMillis = System.currentTimeMillis()

      webSocketOut ! Json.toJson(
        MoveRobot(moveRobot(timeMillis, newTimeMillis, robotState, robotPosition))
      )

    case RobotProgramExited =>
      // TODO do we care? if robot state is (0,0) end here?
      context.stop(self)
  }

  override def receive = receive(
    System.currentTimeMillis,
    RobotState(velocityMmS = 0, radiusMm = None),
    RobotPosition(topMm = 2500, leftMm = 2500, orientationRads = 0.0)
  )

  override def postStop(): Unit = {
    executorService.shutdownNow()
  }
}
