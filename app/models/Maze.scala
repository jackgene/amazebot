package models

import scala.collection.immutable._

/**
  * Represents a maze.
  */
case class Point(topMm: Int, leftMm: Int) {
  assert(topMm >= 0, "topMm must be non-negative")
  assert(leftMm >= 0, "leftMm must be non-negative")
}
object Maze {
  // TODO consider making the size adjustable
  val HeightMm: Int = 5000
  val WidthMm: Int = 5000

  sealed abstract class Obstruction
  case object TopBoundary extends Obstruction
  case object RightBoundary extends Obstruction
  case object BottomBoundary extends Obstruction
  case object LeftBoundary extends Obstruction
  object Wall {
    val MinThicknessMm = 10
  }
  case class Wall(topLeft: Point, bottomRight: Point) extends Obstruction {
    import Wall._
    assert(height >= MinThicknessMm, s"wall height must be at least ${MinThicknessMm}mm")
    assert(width >= MinThicknessMm, s"wall width must be at least ${MinThicknessMm}mm")

    lazy val height: Int = bottomRight.topMm - topLeft.topMm
    lazy val width: Int = bottomRight.leftMm - topLeft.leftMm
  }

  val theMaze = Maze(
    startPoint = Point(500, 500), startOrientationRad = math.Pi, finish = Point(500, 4500),
    walls = Set(
      Wall(Point(0, 1000), Point(1000, 4000)),
      Wall(Point(3000, 0), Point(5000, 5000))
    )
  )
  val byName: Map[String,Maze] = Map(
    "level0" -> Maze(
      startPoint = Point(500, 2500), startOrientationRad = math.Pi, finish = Point(2500, 2500),
      walls = Set()
    ),
    "level1" -> Maze(
      startPoint = Point(500, 500), startOrientationRad = math.Pi, finish = Point(500, 4500),
      walls = Set(
        Wall(Point(0, 1000), Point(1000, 4000)),
        Wall(Point(3000, 0), Point(5000, 5000))
      )
    ),
    "level2" -> Maze(
      startPoint = Point(3500, 500), startOrientationRad = math.Pi / 2, finish = Point(500, 4500),
      walls = Set(
        Wall(Point(0, 0), Point(3000, 2000)),
        Wall(Point(1000, 3000), Point(5000, 5000)),
        Wall(Point(4000, 0), Point(5000, 3000))
      )
    ),
    "level3" -> Maze(
      startPoint = Point(3749, 417), startOrientationRad = math.Pi / 2, finish = Point(417, 4582),
      walls = Set(
        Wall(Point(0, 0), Point(1667, 3333)),
        Wall(Point(833, 4167), Point(5000, 5000)),
        Wall(Point(1667, 0), Point(3333, 1667)),
        Wall(Point(2500, 2500), Point(5000, 4167)),
        Wall(Point(4167, 833), Point(5000, 2500))
      )
    ),
    "level4" -> Maze(
      startPoint = Point(417, 417), startOrientationRad = math.Pi, finish = Point(4582, 4582),
      walls = Set(
        Wall(Point(0, 803), Point(863, 863)),
        Wall(Point(803, 803), Point(863, 1697)),
        Wall(Point(803, 2470), Point(863, 5000)),
        Wall(Point(1637, 0), Point(1697, 4197)),
        Wall(Point(1637, 803), Point(2530, 863)),
        Wall(Point(1637, 1637), Point(3363, 1697)),
        Wall(Point(1637, 2470), Point(2530, 2530)),
        Wall(Point(1637, 4137), Point(2530, 4197)),
        Wall(Point(2470, 3303), Point(2530, 4197)),
        Wall(Point(3303, 803), Point(4197, 863)),
        Wall(Point(3303, 803), Point(3363, 2530)),
        Wall(Point(3303, 3303), Point(3363, 5000)),
        Wall(Point(4137, 1637), Point(5000, 1697)),
        Wall(Point(4137, 2470), Point(5000, 2530)),
        Wall(Point(4137, 2470), Point(4197, 3363)),
        Wall(Point(4137, 4137), Point(4197, 5000))
      )
    )
  )
}
case class Maze(startPoint: Point, startOrientationRad: Double, finish: Point, walls: Set[Maze.Wall]) {
  import Maze._

  private val obstructionsByTopEdge: SortedMap[Double,Set[Obstruction]] =
    SortedMap[Double,Set[Obstruction]](
      Double.NegativeInfinity -> Set(TopBoundary, RightBoundary, LeftBoundary),
      5000.0 -> Set(BottomBoundary)
    ) ++
    walls.groupBy(_.topLeft.topMm.toDouble).asInstanceOf[Map[Double,Set[Obstruction]]]
  private val obstructionsByRightEdge: SortedMap[Double,Set[Obstruction]] =
    SortedMap[Double,Set[Obstruction]](
      Double.PositiveInfinity -> Set(TopBoundary, RightBoundary, BottomBoundary),
      0.0 -> Set(LeftBoundary)
    ) ++
    walls.groupBy(_.bottomRight.leftMm.toDouble).asInstanceOf[Map[Double,Set[Obstruction]]]
  private val obstructionsByBottomEdge: SortedMap[Double,Set[Obstruction]] =
    SortedMap[Double,Set[Obstruction]](
      0.0 -> Set(TopBoundary),
      Double.PositiveInfinity -> Set(RightBoundary, BottomBoundary, LeftBoundary)
    ) ++
    walls.groupBy(_.bottomRight.topMm.toDouble).asInstanceOf[Map[Double,Set[Obstruction]]]
  private val obstructionsByLeftEdge: SortedMap[Double,Set[Obstruction]] =
    SortedMap[Double,Set[Obstruction]](
      Double.NegativeInfinity -> Set(TopBoundary, BottomBoundary, LeftBoundary),
      5000.0 -> Set(RightBoundary)
    ) ++
    walls.groupBy(_.topLeft.leftMm.toDouble).asInstanceOf[Map[Double,Set[Obstruction]]]

  def obstructionsInContact(robotPosition: RobotPosition): Set[Obstruction] = {
    import RobotPosition.RobotSizeRadiusMm

    val robotTopMm: Double = robotPosition.topMm - RobotSizeRadiusMm
    val robotRightMm: Double = robotPosition.leftMm + RobotSizeRadiusMm
    val robotLeftMm: Double = robotPosition.leftMm - RobotSizeRadiusMm
    val robotBottomMm: Double = robotPosition.topMm + RobotSizeRadiusMm
    (
      obstructionsByTopEdge.to(robotBottomMm).values.toSet.flatten
      intersect
      obstructionsByRightEdge.from(robotLeftMm).values.toSet.flatten
      intersect
      obstructionsByBottomEdge.from(robotTopMm).values.toSet.flatten
      intersect
      obstructionsByLeftEdge.to(robotRightMm).values.toSet.flatten
    ).
    filter {
      case Wall(Point(wallTopMm, wallLeftMm), _) if robotTopMm < wallTopMm && robotLeftMm < wallLeftMm =>
        val yDis = robotPosition.topMm - wallTopMm
        val xDis = robotPosition.leftMm - wallLeftMm
        yDis * yDis + xDis * xDis < RobotPosition.RobotSizeRadiusMmSq

      case Wall(Point(wallTopMm, _), Point(_, wallRightMm)) if robotTopMm < wallTopMm && robotRightMm > wallRightMm =>
        val yDis = robotPosition.topMm - wallTopMm
        val xDis = robotPosition.leftMm - wallRightMm
        yDis * yDis + xDis * xDis < RobotPosition.RobotSizeRadiusMmSq

      case Wall(_, Point(wallBottomMm, wallRightMm)) if robotBottomMm > wallBottomMm && robotRightMm > wallRightMm =>
        val yDis = robotPosition.topMm - wallBottomMm
        val xDis = robotPosition.leftMm - wallRightMm
        yDis * yDis + xDis * xDis < RobotPosition.RobotSizeRadiusMmSq

      case Wall(Point(_, wallLeftMm), Point(wallBottomMm, _)) if robotBottomMm > wallBottomMm && robotLeftMm < wallLeftMm =>
        val yDis = robotPosition.topMm - wallBottomMm
        val xDis = robotPosition.leftMm - wallLeftMm
        yDis * yDis + xDis * xDis < RobotPosition.RobotSizeRadiusMmSq

      case _ => true
    }
  }

  def hasFinished(robotPosition: RobotPosition): Boolean = {
    val yDis = robotPosition.topMm - finish.topMm
    val xDis = robotPosition.leftMm - finish.leftMm

    yDis * yDis + xDis * xDis < RobotPosition.RobotSizeRadiusMmSq
  }
}
