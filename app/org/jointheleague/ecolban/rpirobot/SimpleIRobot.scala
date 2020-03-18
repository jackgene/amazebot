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

  val MaxVelocityMmS: Int = 500
  val MinVelocityMms: Int = -500
  val MaxRadiusMm: Int = 2000
  val MinRadiusMm: Int = -2000

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
  private def unsupported: Nothing = throw new UnsupportedOperationException

  override def isCliffFrontLeft: Boolean = unsupported

  override def getBatteryCapacity: Int = unsupported

  override def getInfraredByte: Int = unsupported

  override def ledsToggle(b: Boolean): Unit = unsupported

  override def getBatteryTemperature: Int = unsupported

  override def isBumpRight: Boolean = unsupported

  override def isVirtualWall: Boolean = unsupported

  override def song(i: Int, ints: Array[Int]): Unit = unsupported

  override def song(i: Int, ints: Array[Int], i1: Int, i2: Int): Unit = unsupported

  override def getVoltage: Int = unsupported

  override def isCliffFrontRight: Boolean = unsupported

  override def getMotorCurrentRight: Int = unsupported

  override def isSpotButtonDown: Boolean = unsupported

  override def getRequestedVelocityLeft: Int = unsupported

  override def isSongPlaying: Boolean = unsupported

  override def waitButtonPressed(b: Boolean): Unit = unsupported

  override def isBumpLeft: Boolean = unsupported

  override def isLightBump: Boolean = unsupported

  override def getCliffSignalLeftFront: Int = unsupported

  override def getRequestedRadius: Int = unsupported

  override def getMotorCurrentLeft: Int = unsupported

  override def setTailLight(b: Boolean): Unit = unsupported

  override def getLightBumps: Array[Int] = unsupported

  override def getSongNumber: Int = unsupported

  override def getOiMode: Int = unsupported

  override def isStasis: Boolean = unsupported

  override def isWheelOvercurrentSideBrush: Boolean = unsupported

  override def isCliffRight: Boolean = unsupported

  override def getRequestedVelocity: Int = unsupported

  override def playSong(i: Int): Unit = unsupported

  override def getEncoderCountLeft: Int = unsupported

  override def getCurrent: Int = unsupported

  override def safe(): Unit = unsupported

  override def isRightWheelOvercurrent: Boolean = unsupported

  override def getEncoderCountRight: Int = unsupported

  override def getInfraredByteLeft: Int = unsupported

  override def getRequestedVelocityRight: Int = unsupported

  override def getInfraredByteRight: Int = unsupported

  override def isWheelDropLeft: Boolean = unsupported

  override def isWheelOvercurrentMainBrush: Boolean = unsupported

  override def getBatteryCharge: Int = unsupported

  override def isCliffLeft: Boolean = unsupported

  override def isCleanButtonDown: Boolean = unsupported

  override def leds(i: Int, i1: Int, b: Boolean): Unit = unsupported

  override def isHomeBaseChargerAvailable: Boolean = unsupported

  override def getCliffSignalRight: Int = unsupported

  override def isWheelDropRight: Boolean = unsupported

  override def getCliffSignalLeft: Int = unsupported

  override def getChargingState: Int = unsupported

  override def isInternalChargerAvailable: Boolean = unsupported

  override def full(): Unit = unsupported

  override def getCliffSignalRightFront: Int = unsupported

  override def isWall: Boolean = unsupported

  override def getWallSignal: Int = unsupported

  override def isLeftWheelOvercurrent: Boolean = unsupported
}
