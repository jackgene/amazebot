package actors

import java.lang.reflect.Method
import java.util.concurrent.{Executors, ThreadFactory}

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import models.{RobotPosition, RobotState}
import org.jointheleague.ecolban.rpirobot.SimpleIRobot
import play.api.libs.json.{JsPath, Json, Writes}
import play.api.libs.functional.syntax._

import scala.concurrent.{ExecutionContext, Future}

object SimulationRunActor {
  // Incoming messages
  case object UpdateView
  case object RobotProgramExited
  case class DriveDirect(leftVelocity: Int, rightVelocity: Int)

  // Outgoing messages
  case class MoveRobot(position: RobotPosition)

  // JSON writes
  implicit val moveRobotWrites: Writes[MoveRobot] = (
    (JsPath \ "c").write[String] and
    (JsPath \ "t").write[Int] and
    (JsPath \ "l").write[Int] and
    (JsPath \ "o").write[Double]
  )(
    {
      case MoveRobot(position: RobotPosition) =>
        ("mv", position.top, position.left, position.orientationTurns)
    }: MoveRobot => (String,Int,Int,Double)
  )
  def props(webSocketOut: ActorRef, main: Method): Props = {
    Props(classOf[SimulationRunActor], webSocketOut, main)
  }
}
class SimulationRunActor(webSocketOut: ActorRef, main: Method) extends Actor with ActorLogging {
  import SimulationRunActor._

  var t: Thread = _
  val executorService = Executors.newSingleThreadExecutor(
    new ThreadFactory {
      override def newThread(r: Runnable): Thread = {
        new Thread(r) {
          {
            setPriority(Thread.MIN_PRIORITY)
          }

          // TODO put SecurityManager here

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
      self ! RobotProgramExited
    } recoverWith {
      case t: Throwable =>
        println("Exception in user program")
        t.printStackTrace()
        Future.failed(t)
    }
  }

  private def receive(timeMillis: Long, robotState: RobotState, robotPosition: RobotPosition): Receive = {
    case DriveDirect(leftVelocity, rightVelocity) =>
      val newTimeMillis = System.currentTimeMillis()
      val newRobotPosition: RobotPosition = robotState match {
        case RobotState(0, 0) => robotPosition

        case RobotState(leftVelocity: Int, rightVelocity: Int) if leftVelocity == rightVelocity =>
          // TODO longer time = larger move
//          val durationMillis = newTimeMillis - timeMillis
          robotPosition.copy(
            top = robotPosition.orientationTurns % 1.0 match {
              case 0.0 => robotPosition.top - 30
              case 0.5 => robotPosition.top + 30
              case _ => robotPosition.top
            },
            left = robotPosition.orientationTurns % 1.0 match {
              case 0.25 => robotPosition.left + 30
              case 0.75 => robotPosition.left - 30
              case _ => robotPosition.left
            }
          )

        case RobotState(leftVelocity: Int, rightVelocity: Int) if leftVelocity == -rightVelocity =>
          // TODO longer time = wider turn
          robotPosition.copy(
            orientationTurns = robotPosition.orientationTurns + (if (leftVelocity > 0) 0.25 else -0.25)
          )
      }

      webSocketOut ! Json.toJson(MoveRobot(newRobotPosition))
      context.become(
        receive(
          newTimeMillis,
          RobotState(leftVelocity, rightVelocity),
          newRobotPosition
        )
      )

    case RobotProgramExited =>
      // TODO do we care? if robot state is (0,0) end here?
      context.stop(self)

    case msg =>
      log.warning(s"Received unexpected $msg")
  }
  override def receive = receive(System.currentTimeMillis, RobotState(0, 0), RobotPosition(235, 235, 0.0))

  override def postStop(): Unit = {
    executorService.shutdownNow()
  }
}
