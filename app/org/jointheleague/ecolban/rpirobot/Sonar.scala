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
    val directionRad: Double = position match {
      case "right" => math.Pi / 2
      case "left" => -math.Pi / 2
      case "center" => 0.0
    }
    Await.result(
      (simulationRun ? SimulationRunActor.Ping(directionRad)).map {
        case SimulationRunActor.Pong(distanceMm) => (distanceMm / 10).toInt // Sonar API returns distance in cm
      },
      1.second
    )
  }
}
