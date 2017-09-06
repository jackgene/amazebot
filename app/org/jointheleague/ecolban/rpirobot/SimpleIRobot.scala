package org.jointheleague.ecolban.rpirobot

import actors.SimulationRunActor
import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.math.{max, min}

/**
  * Simulated SimpleIRobot.
  *
  * @author Jack Leow
  * @since August 2017
  */
object SimpleIRobot {
  val AfterCommandPauseTimeMillis = 20
}
class SimpleIRobot extends IRobotInterface {
  import SimpleIRobot._
  import IRobotInterface._

  val MaxVelocityMmS = 500
  val MinVelocityMms = -500
  val MaxRadiusMm = 2000
  val MinRadiusMm = -2000

  private val simulationRun: ActorRef = SimulationRunActor.simulationRunHolder.get
  private implicit val AskTimeout: Timeout = 1.second

  private var angleDegrees: Int = 0
  private var distanceMm: Int = 0

  private def afterCommandPause(): Unit = Thread.sleep(AfterCommandPauseTimeMillis)

  // Movement
  private def adjustedVelocity(velocityMms: Int) =
    if (velocityMms > 0) min(velocityMms, MaxVelocityMmS)
    else max(velocityMms, MinVelocityMms)

  private def adjustedRadius(radiusMm: Int) =
    if (radiusMm > 0) min(radiusMm, MaxRadiusMm)
    else max(radiusMm, MinRadiusMm)

  override def drive(velocityMmS: Int, radiusMm: Int): Unit = {
    val adjVelocityMms: Int = adjustedVelocity(velocityMmS)
    radiusMm match {
      // Special case: straight line, per {@link IRobotInterface}
      case 0x7FFF|0x8000 =>
        simulationRun ! SimulationRunActor.Drive(adjVelocityMms, None)

      // Special case: turn in place clockwise, per {@link IRobotInterface}
      case 0xFFFF =>
        simulationRun ! SimulationRunActor.Drive(adjVelocityMms, Some(0.0))

      // Special case: turn in place counter-clockwise, per {@link IRobotInterface}
      case 0x0001 =>
        simulationRun ! SimulationRunActor.Drive(-adjVelocityMms, Some(0.0))

      case r =>
        simulationRun ! SimulationRunActor.Drive(
          adjVelocityMms, Some(adjustedRadius(r))
        )
    }
    afterCommandPause()
  }

  override def driveDirect(leftVelocity: Int, rightVelocity: Int): Unit = {
    (adjustedVelocity(leftVelocity),adjustedVelocity(rightVelocity)) match {
      case (adjLeftVelocity, adjRightVelocity) if adjLeftVelocity == adjRightVelocity =>
        simulationRun ! SimulationRunActor.Drive(adjLeftVelocity, None)

      case (adjLeftVelocity, adjRightVelocity) if adjLeftVelocity == -adjRightVelocity =>
        simulationRun ! SimulationRunActor.Drive(adjLeftVelocity, Some(0.0))

      case (adjLeftVelocity, adjRightVelocity) =>
        simulationRun ! SimulationRunActor.Drive(
          (adjLeftVelocity + adjRightVelocity) / 2,
          (adjLeftVelocity, adjRightVelocity) match {
            case (0, _) => Some(IRobotInterface.WHEEL_DISTANCE / 2.0)
            case (_, 0) => Some(IRobotInterface.WHEEL_DISTANCE / -2.0)
            case (alv, arv) =>
              val ratio: Double = alv / arv.toDouble
              Some((ratio + 1) * IRobotInterface.WHEEL_DISTANCE / 2.0 / (1 - ratio))
          }
        )
    }
    afterCommandPause()
  }

  override def stop(): Unit = {
    simulationRun ! SimulationRunActor.Drive(0, None)
    afterCommandPause()
  }

  // Sensors
  override def readSensors(sensorId: Int): Unit = {
    sensorId match {
      case SENSORS_DISTANCE =>
        for {
          SimulationRunActor.DistanceSensorValue(distMmOpt: Option[Double]) <-
            simulationRun ? SimulationRunActor.ReadDistanceSensor
        } {
          for (distMm: Double <- distMmOpt) {
            distanceMm = (distMm + 0.5).toInt
          }
        }

      case SENSORS_ANGLE =>
        for {
          SimulationRunActor.AngleSensorValue(angleRadOpt: Option[Double]) <-
            simulationRun ? SimulationRunActor.ReadAngleSensor
        } {
          for (angleRad: Double <- angleRadOpt) {
            angleDegrees = (angleRad / math.Pi * 180 + 0.5).toInt
          }
        }

      case _ => throw new UnsupportedOperationException("The requested sensor is not supported")
    }
    afterCommandPause()
  }

  override def getDistance: Int = distanceMm

  override def getAngle: Int = angleDegrees

  // Resource management
  override def closeConnection(): Unit = {}

  override def reset(): Unit = {}

  // Unsupported
  override def isCliffFrontLeft: Boolean = ???

  override def getBatteryCapacity: Int = ???

  override def getInfraredByte: Int = ???

  override def ledsToggle(b: Boolean): Unit = ???

  override def getBatteryTemperature: Int = ???

  override def isBumpRight: Boolean = ???

  override def isVirtualWall: Boolean = ???

  override def song(i: Int, ints: Array[Int]): Unit = ???

  override def song(i: Int, ints: Array[Int], i1: Int, i2: Int): Unit = ???

  override def getVoltage: Int = ???

  override def isCliffFrontRight: Boolean = ???

  override def getMotorCurrentRight: Int = ???

  override def isSpotButtonDown: Boolean = ???

  override def getRequestedVelocityLeft: Int = ???

  override def isSongPlaying: Boolean = ???

  override def waitButtonPressed(b: Boolean): Unit = ???

  override def isBumpLeft: Boolean = ???

  override def isLightBump: Boolean = ???

  override def getCliffSignalLeftFront: Int = ???

  override def getRequestedRadius: Int = ???

  override def getMotorCurrentLeft: Int = ???

  override def setTailLight(b: Boolean): Unit = ???

  override def getLightBumps: Array[Int] = ???

  override def getSongNumber: Int = ???

  override def getOiMode: Int = ???

  override def isStasis: Boolean = ???

  override def isWheelOvercurrentSideBrush: Boolean = ???

  override def isCliffRight: Boolean = ???

  override def getRequestedVelocity: Int = ???

  override def playSong(i: Int): Unit = ???

  override def getEncoderCountLeft: Int = ???

  override def getCurrent: Int = ???

  override def safe(): Unit = ???

  override def isRightWheelOvercurrent: Boolean = ???

  override def getEncoderCountRight: Int = ???

  override def getInfraredByteLeft: Int = ???

  override def getRequestedVelocityRight: Int = ???

  override def getInfraredByteRight: Int = ???

  override def isWheelDropLeft: Boolean = ???

  override def isWheelOvercurrentMainBrush: Boolean = ???

  override def getBatteryCharge: Int = ???

  override def isCliffLeft: Boolean = ???

  override def isCleanButtonDown: Boolean = ???

  override def leds(i: Int, i1: Int, b: Boolean): Unit = ???

  override def isHomeBaseChargerAvailable: Boolean = ???

  override def getCliffSignalRight: Int = ???

  override def isWheelDropRight: Boolean = ???

  override def getCliffSignalLeft: Int = ???

  override def getChargingState: Int = ???

  override def isInternalChargerAvailable: Boolean = ???

  override def full(): Unit = ???

  override def getCliffSignalRightFront: Int = ???

  override def isWall: Boolean = ???

  override def getWallSignal: Int = ???

  override def isLeftWheelOvercurrent: Boolean = ???
}
