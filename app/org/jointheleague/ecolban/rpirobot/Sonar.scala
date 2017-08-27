package org.jointheleague.ecolban.rpirobot

import actors.SimulationRunActor
import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

/**
  * Simulated Sonar.
  *
  * @author Jack Leow
  * @since August 2017
  */
object Sonar {
}
class Sonar {
  private val simulationRun: ActorRef = SimulationRunActor.simulationRunHolder.get
  private implicit val AskTimeout: Timeout = 1.second

  def readSonar(position: String): Int = {
    val directionRadOpt: Option[Double] = position match {
      case "right" => Some(math.Pi / 2)
      case "left" => Some(-math.Pi / 2)
      case "center" => Some(0.0)
      case _ => None
    }

    directionRadOpt.map { directionRad: Double =>
      Await.result(
        (simulationRun ? SimulationRunActor.Ping(directionRad)).map {
          case SimulationRunActor.Pong(distanceMm) => (distanceMm / 10).toInt // Sonar API returns distance in cm
        },
        1.second
      )
    }.
    getOrElse(-1)
  }
}
