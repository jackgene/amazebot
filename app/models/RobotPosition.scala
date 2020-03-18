package models

/**
  * Data about the robot's position relative to the map.
  */
object RobotPosition {
  val RobotSizeRadiusMm: Double = 173.5
  val RobotSizeRadiusMmSq: Double = RobotSizeRadiusMm * RobotSizeRadiusMm
}
case class RobotPosition(
  topMm: Double,
  leftMm: Double,
  orientationRad: Double
)
