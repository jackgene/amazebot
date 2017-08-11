package org.jointheleague.ecolban.rpirobot

import actors.SimulationRunActor
import akka.actor.ActorRef

object SimpleIRobot {
  val simulationRunHolder = new ThreadLocal[ActorRef]
}
class SimpleIRobot extends IRobotInterface {
  import SimpleIRobot._

  private val simulationRun: ActorRef = simulationRunHolder.get

  override def driveDirect(leftVelocity: Int, rightVelocity: Int) {
    simulationRun ! SimulationRunActor.DriveDirect(leftVelocity, rightVelocity)
  }

  override def closeConnection() {}

  override def reset() {}

  override def stop() {}

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

  override def drive(i: Int, i1: Int): Unit = ???

  override def getWallSignal: Int = ???

  override def isLeftWheelOvercurrent: Boolean = ???
}
