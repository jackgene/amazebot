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
  radiusMm: Option[Double]
)
