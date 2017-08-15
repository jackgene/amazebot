package actors

import java.lang.reflect.Method
import java.util.concurrent.{Executors, ThreadFactory}

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import models.{RobotPosition, RobotState}
import org.jointheleague.ecolban.rpirobot.{IRobotInterface, SimpleIRobot}
import play.api.libs.functional.syntax._
import play.api.libs.json.{JsPath, Json, Writes}

import scala.concurrent.{ExecutionContext, Future}
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

  private def receive(timeMillis: Long, robotState: RobotState, robotPosition: RobotPosition): Receive = {
    case Drive(newVelocityMmS: Double, newRadiusMm: Option[Double]) =>
      val newTimeMillis = System.currentTimeMillis()
      val durationSecs = (newTimeMillis - timeMillis) / 1000.0
      val newRobotPosition: RobotPosition = robotState match {
        // Not moving
        case RobotState(0, _) => robotPosition

        // Turning in place
        case RobotState(velocityMmS: Double, Some(0.0)) =>
          robotPosition.copy(
            orientationRads =
              robotPosition.orientationRads +
              (velocityMmS * durationSecs) / WheelDisplacementMmPerRadian
          )

        // Moving in a straight line
        case RobotState(velocityMmS: Double, None) =>
          val displacementMm = velocityMmS * durationSecs
          val orientationRad = robotPosition.orientationRads

          robotPosition.copy(
            topMm = robotPosition.topMm - (cos(orientationRad) * displacementMm),
            leftMm = robotPosition.leftMm + (sin(orientationRad) * displacementMm)
          )

        // Moving in a curve
        case RobotState(velocityMmS: Double, Some(radiusMm: Double)) =>
          val displacementMm = velocityMmS * durationSecs
          val orientationDeltaRads = displacementMm / -radiusMm.toDouble
          val orientationRads = robotPosition.orientationRads
          val newOrientationRads = orientationRads + orientationDeltaRads
          val axisTop = robotPosition.topMm - (sin(orientationRads) * radiusMm)
          val axisLeft = robotPosition.leftMm - (cos(orientationRads) * radiusMm)

          robotPosition.copy(
            topMm = axisTop + sin(newOrientationRads) * radiusMm,
            leftMm = axisLeft + cos(newOrientationRads) * radiusMm,
            orientationRads = robotPosition.orientationRads + orientationDeltaRads
          )
      }

      webSocketOut ! Json.toJson(MoveRobot(newRobotPosition))
      context.become(
        receive(
          newTimeMillis,
          RobotState(newVelocityMmS, newRadiusMm),
          newRobotPosition
        )
      )

    case UpdateView =>

    case RobotProgramExited =>
      // TODO do we care? if robot state is (0,0) end here?
      context.stop(self)

    case msg =>
      log.warning(s"Received unexpected $msg")
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
