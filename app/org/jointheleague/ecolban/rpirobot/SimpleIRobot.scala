package org.jointheleague.ecolban.rpirobot

import actors.SimulationRunActor
import akka.actor.ActorRef

import scala.math.{max, min}

object SimpleIRobot {
  val simulationRunHolder = new ThreadLocal[ActorRef]

  val AfterCommandPauseTimeMillis = 20
}
class SimpleIRobot extends IRobotInterface {
  import SimpleIRobot._

  val MaxVelocityMmS = 500
  val MinVelocityMms = -500
  val MaxRadiusMm = 2000
  val MinRadiusMm = -2000

  private val simulationRun: ActorRef = simulationRunHolder.get

  private def afterCommandPause(): Unit = Thread.sleep(AfterCommandPauseTimeMillis)

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

  override def closeConnection(): Unit = {}

  override def reset(): Unit = {}

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

  override def getAngle: Int = ???

  override def isWheelDropRight: Boolean = ???

  override def getCliffSignalLeft: Int = ???

  override def getChargingState: Int = ???

  override def isInternalChargerAvailable: Boolean = ???

  override def getDistance: Int = ???

  override def full(): Unit = ???

  override def getCliffSignalRightFront: Int = ???

  override def readSensors(i: Int): Unit = ???

  override def isWall: Boolean = ???

  override def getWallSignal: Int = ???

  override def isLeftWheelOvercurrent: Boolean = ???
}
