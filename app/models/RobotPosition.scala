package models

/**
  * Data about the robot's position relative to the map.
  */
object RobotPosition {
  val RobotSizeRadiusMm = 173.5
  val RobotSizeRadiusMmSq = RobotSizeRadiusMm * RobotSizeRadiusMm
}
case class RobotPosition(
  topMm: Double,
  leftMm: Double,
  orientationRad: Double
)
