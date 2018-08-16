package models

import scala.annotation.tailrec
import scala.collection.immutable._
import scala.util.Random

/**
  * Represents a maze.
  */
case class Point(topMm: Int, leftMm: Int) {
  assert(topMm >= 0, "topMm must be non-negative")
  assert(leftMm >= 0, "leftMm must be non-negative")
}
object Maze {
  val HeightMm: Int = 5000
  val WidthMm: Int = 5000

  sealed abstract class Obstruction(
    // Distance of edges from absolute top/left in mm
    val top: Double,
    val right: Double,
    val bottom: Double,
    val left: Double
  )
  case object TopBoundary extends Obstruction(
    top = Double.NegativeInfinity,
    right = Double.PositiveInfinity,
    bottom = 0.0,
    left = Double.NegativeInfinity
  )
  case object RightBoundary extends Obstruction(
    top = Double.NegativeInfinity,
    right = Double.PositiveInfinity,
    bottom = Double.PositiveInfinity,
    left = WidthMm
  )
  case object BottomBoundary extends Obstruction(
    top = HeightMm,
    right = Double.PositiveInfinity,
    bottom = Double.PositiveInfinity,
    left = Double.NegativeInfinity
  )
  case object LeftBoundary extends Obstruction(
    top = Double.NegativeInfinity,
    right = 0.0,
    bottom = Double.PositiveInfinity,
    left = Double.NegativeInfinity
  )
  object Wall {
    val MinThicknessMm = 10
  }
  case class Wall(topLeft: Point, bottomRight: Point) extends Obstruction (
    top = topLeft.topMm,
    right = bottomRight.leftMm,
    bottom = bottomRight.topMm,
    left = topLeft.leftMm
  ) {
    import Wall._
    assert(heightMm >= MinThicknessMm, s"wall height must be at least ${MinThicknessMm}mm")
    assert(widthMm >= MinThicknessMm, s"wall width must be at least ${MinThicknessMm}mm")

    lazy val heightMm: Int = bottomRight.topMm - topLeft.topMm
    lazy val widthMm: Int = bottomRight.leftMm - topLeft.leftMm
  }
  // Virtual obstructions to faciliate maths
  private case class RobotEdges(position: RobotPosition) extends Obstruction(
    // Intentionally inverted to facilitate computation
    top = position.topMm + RobotPosition.RobotSizeRadiusMm,
    right = position.leftMm - RobotPosition.RobotSizeRadiusMm,
    bottom = position.topMm - RobotPosition.RobotSizeRadiusMm,
    left = position.leftMm + RobotPosition.RobotSizeRadiusMm
  )
  private case class RobotCenter(position: RobotPosition) extends Obstruction(
    top = position.topMm,
    right = position.leftMm,
    bottom = position.topMm,
    left = position.leftMm
  )

  val byName: Map[String,UserDefinedMaze] = Map(
    "level0" -> UserDefinedMaze(
      startPoint = Point(500, 2500), startOrientationRad = math.Pi, finish = Point(2500, 2500),
      walls = Set()
    ),
    "level1" -> UserDefinedMaze(
      startPoint = Point(500, 500), startOrientationRad = math.Pi, finish = Point(500, 4500),
      walls = Set(
        Wall(Point(0, 1000), Point(1000, 4000)),
        Wall(Point(3000, 0), Point(5000, 5000))
      )
    ),
    "level2" -> UserDefinedMaze(
      startPoint = Point(3500, 500), startOrientationRad = math.Pi / 2, finish = Point(500, 4500),
      walls = Set(
        Wall(Point(0, 0), Point(3000, 2000)),
        Wall(Point(1000, 3000), Point(5000, 5000)),
        Wall(Point(4000, 0), Point(5000, 3000))
      )
    ),
    "level3" -> UserDefinedMaze(
      startPoint = Point(3749, 417), startOrientationRad = math.Pi / 2, finish = Point(417, 4582),
      walls = Set(
        Wall(Point(0, 0), Point(1667, 3333)),
        Wall(Point(833, 4167), Point(5000, 5000)),
        Wall(Point(1667, 0), Point(3333, 1667)),
        Wall(Point(2500, 2500), Point(5000, 4167)),
        Wall(Point(4167, 833), Point(5000, 2500))
      )
    ),
    "level4" -> UserDefinedMaze(
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

  def random(verticalCells: Int, horizontalCells: Int, rng: Random): GeneratedMaze = {
    val CellHeightMm: Int = Maze.HeightMm / verticalCells
    val CellWidthMm: Int = Maze.WidthMm / horizontalCells
    val HalfWallThicknessMm: Int = 30

    def wallBetween(cell1: (Int,Int), cell2: (Int,Int)): Option[Wall] = (cell1, cell2) match {
      case ((leftCellTop: Int, leftCellLeft: Int), (rightCellTop: Int, rightCellLeft: Int))
          if leftCellTop == rightCellTop && (rightCellLeft - leftCellLeft) == 1 =>
        Some(
          Wall(
            Point(
              math.max(0, leftCellTop * CellHeightMm - HalfWallThicknessMm),
              rightCellLeft * CellWidthMm - HalfWallThicknessMm
            ),
            Point(
              math.min(Maze.HeightMm, (leftCellTop + 1) * CellHeightMm + HalfWallThicknessMm),
              rightCellLeft * CellWidthMm + HalfWallThicknessMm
            )
          )
        )

      case ((rightCellTop: Int, rightCellLeft: Int), (leftCellTop: Int, leftCellLeft: Int))
          if leftCellTop == rightCellTop && (rightCellLeft - leftCellLeft) == 1 =>
        Some(
          Wall(
            Point(
              math.max(0, leftCellTop * CellHeightMm - HalfWallThicknessMm),
              rightCellLeft * CellWidthMm - HalfWallThicknessMm
            ),
            Point(
              math.min(Maze.HeightMm, (leftCellTop + 1) * CellHeightMm + HalfWallThicknessMm),
              rightCellLeft * CellWidthMm + HalfWallThicknessMm
            )
          )
        )

      case ((topCellTop: Int, topCellLeft: Int), (bottomCellTop: Int, bottomCellLeft: Int))
          if topCellLeft == bottomCellLeft && (bottomCellTop - topCellTop) == 1 =>
        Some(
          Wall(
            Point(
              bottomCellTop * CellHeightMm - HalfWallThicknessMm,
              math.max(0, topCellLeft * CellWidthMm - HalfWallThicknessMm)
            ),
            Point(
              bottomCellTop * CellHeightMm + HalfWallThicknessMm,
              math.min(Maze.WidthMm, (topCellLeft + 1) * CellWidthMm + HalfWallThicknessMm)
            )
          )
        )

      case ((bottomCellTop: Int, bottomCellLeft: Int), (topCellTop: Int, topCellLeft: Int))
          if topCellLeft == bottomCellLeft && (bottomCellTop - topCellTop) == 1 =>
        Some(
          Wall(
            Point(
              bottomCellTop * CellHeightMm - HalfWallThicknessMm,
              math.max(0, topCellLeft * CellWidthMm - HalfWallThicknessMm)
            ),
            Point(
              bottomCellTop * CellHeightMm + HalfWallThicknessMm,
              math.min(Maze.WidthMm, (topCellLeft + 1) * CellWidthMm + HalfWallThicknessMm)
            )
          )
        )

      case _ => None
    }

    @tailrec def wallsHistory(toVisit: List[(Int,Int)], unvisited: Set[(Int,Int)], accum: List[Set[Wall]]): List[Set[Wall]] =
      if (unvisited.isEmpty) accum
      else {
        val visiting @ (visitingTop: Int, visitingLeft: Int) = toVisit.head
        val unvisitedNeighbors: Set[(Int,Int)] =
          Set(
            (visitingTop - 1, visitingLeft),
            (visitingTop + 1, visitingLeft),
            (visitingTop, visitingLeft - 1),
            (visitingTop, visitingLeft + 1)
          ) & unvisited

        if (unvisitedNeighbors.isEmpty) wallsHistory(toVisit.tail, unvisited, accum)
        else {
          val rndUnvisitedNeighbor: (Int,Int) =
            if (unvisitedNeighbors.size == 1) unvisitedNeighbors.head
            else unvisitedNeighbors.toIndexedSeq(rng.nextInt(unvisitedNeighbors.size))
          val walls: Set[Wall] = wallBetween(visiting, rndUnvisitedNeighbor) match {
            case Some(wall: Wall) => accum.head - wall
            case None => accum.head
          }

          wallsHistory(rndUnvisitedNeighbor :: toVisit, unvisited - rndUnvisitedNeighbor, walls :: accum)
        }
      }

    val cells: Set[(Int,Int)] =
      (0 until verticalCells).flatMap { top: Int =>
        (0 until horizontalCells).map { left: Int =>
          (top, left)
        }
      }.
      toSet
    val initialWalls: Set[Wall] = (
      // Vertical walls
      (
        for {
          top: Int <- 0 until verticalCells
          Seq(leftLeft: Int, rightLeft: Int) <- 0 until horizontalCells sliding 2
          wall: Wall <- wallBetween((top, leftLeft), (top, rightLeft))
        } yield wall
      ).
      toSet

      ++

      // Horizontal walls
      (
        for {
          left: Int <- 0 until horizontalCells
          Seq(topTop: Int, bottomTop: Int) <- 0 until verticalCells sliding 2
          wall: Wall <- wallBetween((topTop, left), (bottomTop, left))
        } yield wall
      ).
      toSet
    )
    (Stream.continually(rng.nextInt(verticalCells)) zip Stream.continually(rng.nextInt(horizontalCells))).
      sliding(2).
      collectFirst {
        case (startCell @ (startTop: Int, startLeft)) #:: (finishCell @ (finishTop: Int, finishLeft: Int)) #:: Stream.Empty if startCell != finishCell =>
          GeneratedMaze(
            Point(startTop * CellHeightMm + CellHeightMm / 2, startLeft * CellWidthMm + CellWidthMm / 2),
            rng.nextInt(4) * math.Pi / 2,
            Point(finishTop * CellHeightMm + CellHeightMm / 2, finishLeft * CellWidthMm + CellWidthMm / 2),
            wallsHistory(finishCell :: Nil, cells - finishCell, initialWalls :: Nil)
          )
      }.
      get
  }
}
sealed abstract class Maze {
  import Maze._

  val startPoint: Point
  val startOrientationRad: Double
  val finish: Point
  val walls: Set[Maze.Wall]

  private lazy val obstructionsOrderedByTopEdge: SortedSet[Obstruction] =
    SortedSet[Obstruction](TopBoundary, RightBoundary, BottomBoundary, LeftBoundary)(
      Ordering.by { o: Obstruction =>
        // We mainly care about top edge. The rest are for disambiguation
        (o.top, (o.right, o.bottom, o.left))
      }
    ) ++ walls
  private lazy val obstructionsOrderedByRightEdge: SortedSet[Obstruction] =
    SortedSet[Obstruction](TopBoundary, RightBoundary, BottomBoundary, LeftBoundary)(
      Ordering.by { o: Obstruction =>
        // We mainly care about right edge. The rest are for disambiguation
        (-o.right, (o.top, o.bottom, o.left))
      }
    ) ++ walls
  private lazy val obstructionsOrderedByBottomEdge: SortedSet[Obstruction] =
    SortedSet[Obstruction](TopBoundary, RightBoundary, BottomBoundary, LeftBoundary)(
      Ordering.by { o: Obstruction =>
        // We mainly care about bottom edge. The rest are for disambiguation
        (-o.bottom, (o.top, o.right, o.left))
      }
    ) ++ walls
  private lazy val obstructionsOrderedByLeftEdge: SortedSet[Obstruction] =
    SortedSet[Obstruction](TopBoundary, RightBoundary, BottomBoundary, LeftBoundary)(
      Ordering.by { o: Obstruction =>
        // We mainly care about left edge. The rest are for disambiguation
        (o.left, (o.top, o.right, o.bottom))
      }
    ) ++ walls

  def obstructionsInContact(robotPosition: RobotPosition): Set[Obstruction] = {
    val robotCenterTopMm: Double = robotPosition.topMm
    val robotCenterLeftMm: Double = robotPosition.leftMm

    (
      obstructionsOrderedByTopEdge.to(RobotEdges(robotPosition)) &
      obstructionsOrderedByRightEdge.to(RobotEdges(robotPosition)) &
      obstructionsOrderedByBottomEdge.to(RobotEdges(robotPosition)) &
      obstructionsOrderedByLeftEdge.to(RobotEdges(robotPosition))
    ).
    filter {
      case Wall(Point(wallTopMm, wallLeftMm), _) if robotCenterTopMm < wallTopMm && robotCenterLeftMm < wallLeftMm =>
        val yDis = robotCenterTopMm - wallTopMm
        val xDis = robotCenterLeftMm - wallLeftMm
        yDis * yDis + xDis * xDis < RobotPosition.RobotSizeRadiusMmSq

      case Wall(Point(wallTopMm, _), Point(_, wallRightMm)) if robotCenterTopMm < wallTopMm && robotCenterLeftMm > wallRightMm =>
        val yDis = robotCenterTopMm - wallTopMm
        val xDis = robotCenterLeftMm - wallRightMm
        yDis * yDis + xDis * xDis < RobotPosition.RobotSizeRadiusMmSq

      case Wall(_, Point(wallBottomMm, wallRightMm)) if robotCenterTopMm > wallBottomMm && robotCenterLeftMm > wallRightMm =>
        val yDis = robotCenterTopMm - wallBottomMm
        val xDis = robotCenterLeftMm - wallRightMm
        yDis * yDis + xDis * xDis < RobotPosition.RobotSizeRadiusMmSq

      case Wall(Point(_, wallLeftMm), Point(wallBottomMm, _)) if robotCenterTopMm > wallBottomMm && robotCenterLeftMm < wallLeftMm =>
        val yDis = robotCenterTopMm - wallBottomMm
        val xDis = robotCenterLeftMm - wallLeftMm
        yDis * yDis + xDis * xDis < RobotPosition.RobotSizeRadiusMmSq

      case _ => true
    }
  }

  /**
    * Determines if the robot has reached the finish point.
    *
    * @param robotPosition the position of the robot.
    * @return `true` if the robot has reached the finish point; `false` otherwise
    */
  def hasFinished(robotPosition: RobotPosition): Boolean = {
    val yDis = robotPosition.topMm - finish.topMm
    val xDis = robotPosition.leftMm - finish.leftMm

    yDis * yDis + xDis * xDis < RobotPosition.RobotSizeRadiusMmSq
  }

  private def rightAngleDistanceToClosestObstruction(
      robotPosition: RobotPosition,
      obsAhead: SortedSet[Obstruction], obsToLeft: SortedSet[Obstruction], obsToRight: SortedSet[Obstruction],
      robotSonarEdge: RobotPosition => Double, obsClosestEdge: Obstruction => Double):
      Double = {
    val closestObstruction: Obstruction =
      (
        obsAhead.from(RobotEdges(robotPosition)) &
        obsToLeft.to(RobotCenter(robotPosition)) &
        obsToRight.to(RobotCenter(robotPosition))
      ).
      head

    robotSonarEdge(robotPosition) - obsClosestEdge(closestObstruction)
  }

  /**
    * Robot orientation has to be within 0.5 radians of true N/S/E/W, otherwise returns None.
    *
    * @param robotPosition the position of the robot.
    * @param robotRelativeDirectionRad the direction of obstructions, relative to the robot's orientation.
    * @return the distance of the closest obstruction.
    */
  def distanceToClosestObstruction(robotPosition: RobotPosition, robotRelativeDirectionRad: Double): Option[Double] = {
    val absDir: Double = ((robotPosition.orientationRad + robotRelativeDirectionRad) % (math.Pi * 2) + math.Pi * 2) % (math.Pi * 2)
    (absDir + 0.5) % (math.Pi / 2) - 0.5 match {
      case offCenterRad if offCenterRad < 1.0 => // within +/-0.5 radians
        val rightAngleDistance: Double =
          ((absDir + math.Pi / 4) / (math.Pi / 2)).toInt % 4 match {
            case 0 => // N
              rightAngleDistanceToClosestObstruction(
                robotPosition: RobotPosition,
                obstructionsOrderedByBottomEdge, obstructionsOrderedByRightEdge, obstructionsOrderedByLeftEdge,
                _.topMm - RobotPosition.RobotSizeRadiusMm, _.bottom
              )

            case 1 => // E
              rightAngleDistanceToClosestObstruction(
                robotPosition: RobotPosition,
                obstructionsOrderedByLeftEdge, obstructionsOrderedByTopEdge, obstructionsOrderedByBottomEdge,
                - _.leftMm - RobotPosition.RobotSizeRadiusMm, - _.left
              )

            case 2 => // S
              rightAngleDistanceToClosestObstruction(
                robotPosition: RobotPosition,
                obstructionsOrderedByTopEdge, obstructionsOrderedByLeftEdge, obstructionsOrderedByRightEdge,
                - _.topMm - RobotPosition.RobotSizeRadiusMm, - _.top
              )

            case 3 => // W
              rightAngleDistanceToClosestObstruction(
                robotPosition: RobotPosition,
                obstructionsOrderedByRightEdge, obstructionsOrderedByBottomEdge, obstructionsOrderedByTopEdge,
                _.leftMm - RobotPosition.RobotSizeRadiusMm, _.right
              )
          }

      Some(rightAngleDistance / math.cos(offCenterRad))

      case _ => None
    }
  }
}
case class UserDefinedMaze(startPoint: Point, startOrientationRad: Double, finish: Point, walls: Set[Maze.Wall]) extends Maze
case class GeneratedMaze(startPoint: Point, startOrientationRad: Double, finish: Point, wallsHistory: List[Set[Maze.Wall]]) extends Maze {
  override val walls = wallsHistory.head
}
