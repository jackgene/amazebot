package models

/**
  * Data about the robot's position relative to the map.
  */
case class RobotPosition(
  top: Int,
  left: Int,
  orientationTurns: Double
)
