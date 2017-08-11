package actors

import java.lang.reflect.Method
import java.util.concurrent.{Executors, ThreadFactory}

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import org.jointheleague.ecolban.rpirobot.SimpleIRobot
import play.api.libs.json.Json

import scala.concurrent.{ExecutionContext, Future}

object SimulationRunActor {
  // Messages
  case class DriveDirect(leftVelocity: Int, rightVelocity: Int)

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
      self ! "Done"
    } recoverWith {
      case t: Throwable =>
        t.printStackTrace()
        Future.failed(t)
    }
  }

  override def receive: Receive = {
    case DriveDirect(1, 1) =>
      webSocketOut ! Json.toJson(Seq("f"))

    case DriveDirect(1, -1) =>
      webSocketOut ! Json.toJson(Seq("r"))

    case "Done" =>
      context.stop(self)

    case msg =>
      log.warning(s"Received unexpected $msg")
  }

  override def postStop(): Unit = {
    executorService.shutdownNow()
  }
}
