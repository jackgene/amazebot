package models

/**
  * Data about the robot's position relative to the map.
  */
case class RobotPosition(
  topMm: Double,
  leftMm: Double,
  orientationRads: Double
)
