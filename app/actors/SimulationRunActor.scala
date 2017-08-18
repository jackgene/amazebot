package actors

import java.lang.reflect.Method
import java.util.concurrent.{Executors, ThreadFactory}

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern.pipe
import models.Maze._
import models.{RobotPosition, RobotState}
import org.jointheleague.ecolban.rpirobot.{IRobotInterface, SimpleIRobot}
import play.api.libs.functional.syntax._
import play.api.libs.json.{JsPath, Json, Writes}

import scala.collection.immutable.SortedMap
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.math.{cos, sin}

object SimulationRunActor {
  // Incoming messages
  case object UpdateView
  case object RobotProgramExited
  case class Drive(velocityMmS: Double, radiusMm: Option[Double])

  // Outgoing messages
  case class MoveRobot(position: RobotPosition)
  case class ShowNotification(message: String)

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
  implicit val showNotificationWrites: Writes[ShowNotification] = (
    (JsPath \ "c").write[String] and
    (JsPath \ "m").write[String]
  )(
    {
      case ShowNotification(message: String) =>
        ("msg", message)
    }: ShowNotification => (String,String)
  )

  def props(webSocketOut: ActorRef, main: Method): Props = {
    Props(classOf[SimulationRunActor], webSocketOut, main)
  }

  private val RobotSizeRadius = 173.5
  private val WheelDisplacementMmPerRadian = IRobotInterface.WHEEL_DISTANCE / 2.0
}
class SimulationRunActor(webSocketOut: ActorRef, main: Method) extends Actor with ActorLogging {
  import SimulationRunActor._
  import context.dispatcher

  val obstructionsByTopEdge: SortedMap[Double,Set[Obstruction]] = SortedMap(
    Double.NegativeInfinity -> Set(TopBoundary, RightBoundary, LeftBoundary),
    5000.0 -> Set(BottomBoundary)
  )
  val obstructionsByRightEdge: SortedMap[Double,Set[Obstruction]] = SortedMap(
    Double.PositiveInfinity -> Set(TopBoundary, RightBoundary, BottomBoundary),
    0.0 -> Set(LeftBoundary)
  )
  val obstructionsByBottomEdge: SortedMap[Double,Set[Obstruction]] = SortedMap(
    0.0 -> Set(TopBoundary),
    Double.PositiveInfinity -> Set(RightBoundary, BottomBoundary, LeftBoundary)
  )
  val obstructionsByLeftEdge: SortedMap[Double,Set[Obstruction]] = SortedMap(
    Double.NegativeInfinity -> Set(TopBoundary, BottomBoundary, LeftBoundary),
    5000.0 -> Set(RightBoundary)
  )

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

  val viewUpdateScheduler = context.system.scheduler.schedule(0.millis, 250.millis, self, UpdateView)

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

  private def isObstructed(robotPosition: RobotPosition): Boolean = {
    (
      obstructionsByTopEdge.to(robotPosition.topMm + RobotSizeRadius).values.toSet.flatten
      intersect
      obstructionsByRightEdge.from(robotPosition.leftMm - RobotSizeRadius).values.toSet.flatten
      intersect
      obstructionsByBottomEdge.from(robotPosition.topMm - RobotSizeRadius).values.toSet.flatten
      intersect
      obstructionsByLeftEdge.to(robotPosition.leftMm + RobotSizeRadius).values.toSet.flatten
    ).nonEmpty
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
      val newRobotPosition: RobotPosition =
        moveRobot(timeMillis, newTimeMillis, robotState, robotPosition)

      webSocketOut ! Json.toJson(MoveRobot(newRobotPosition))
      if (isObstructed(newRobotPosition)) {
        webSocketOut ! Json.toJson(ShowNotification("You've hit a wall!"))
        context.stop(self)
      }

    case RobotProgramExited =>
      if (robotState.velocityMmS == 0.0) context.stop(self)
  }

  override def receive = receive(
    System.currentTimeMillis,
    RobotState(velocityMmS = 0, radiusMm = None),
    RobotPosition(topMm = 2500, leftMm = 2500, orientationRads = 0.0)
  )

  override def postStop(): Unit = {
    viewUpdateScheduler.cancel()
    executorService.shutdownNow()
  }

  // Start simulation
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
  } pipeTo context.self // TODO look into why this doesn't really work
}
