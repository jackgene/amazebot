package models

case class RobotState(
  /**
   * Velocity of the robot in mm/s.
   *
   * Valid values are [-500:500]
   */
  velocityMmS: Double,

  /**
   * Turning radius of the robot in mm.
   *
   * Valid values are [-2000:2000], when defined.
   *
   * Absence of a value means the robot is moving in a straight line.
   */
  radiusMm: Option[Double],

  /**
   * Absolute orientation of the robot when the angle sensor was read.
   *
   * Absence of a value means the sensor has never been read.
   */
  orientationRadOnSensorRead: Option[Double] = None,

  /**
    * State of the distance sensor.
    *
    * Used to calculate the distance sensor value:
    * Either an optional wall time in ms when the sensor was read,
    * or the distance recorded until the last drive command.
    *
    * Left(None) means the sensor has never been read.
    */
  distanceSensorState: Either[Option[Long],Double] = Left(None)
)
