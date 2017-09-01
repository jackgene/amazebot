package models

import play.api.libs.functional.syntax._
import play.api.libs.json.{JsPath, Writes}

/**
  * JSON writes to serialize the view update instructions.
  *
  * @author Jack Leow
  * @since August 2017
  */
object ViewUpdateInstructions {
  import actors.SimulationSessionActor._
  import actors.SimulationRunActor._

  private def roundOrient(orientation: Double): Double = math.round(orientation * 100) / 100.0

  implicit val wallWrites: Writes[Maze.Wall] = (
    (JsPath \ "t").write[Int] and
    (JsPath \ "l").write[Int] and
    (JsPath \ "h").write[Int] and
    (JsPath \ "w").write[Int]
  ) {
    wall: Maze.Wall =>
      (wall.topLeft.topMm, wall.topLeft.leftMm, wall.height, wall.width)
  }

  implicit val drawMazeWrites: Writes[DrawMaze] = (
    (JsPath \ "c").write[String] and
    (JsPath \ "ft").write[Int] and
    (JsPath \ "fl").write[Int] and
    (JsPath \ "w").write[List[Set[Maze.Wall]]]
  )(
    {
      case DrawMaze(finish: Point, wallsHistory: List[Set[Maze.Wall]]) =>
        ("maze", finish.topMm, finish.leftMm, wallsHistory)
    }: DrawMaze => (String,Int,Int,List[Set[Maze.Wall]])
  )

  implicit val initializeRobotWrites: Writes[InitializeRobot] = (
    (JsPath \ "c").write[String] and
    (JsPath \ "t").write[Double] and
    (JsPath \ "l").write[Double] and
    (JsPath \ "o").write[Double]
  )(
    {
      case InitializeRobot(position: RobotPosition) =>
        ("init", position.topMm, position.leftMm, position.orientationRad)
    }: InitializeRobot => (String,Double,Double,Double)
  )

  implicit val printToConsoleWrites: Writes[PrintToConsole] = (
    (JsPath \ "c").write[String] and
    (JsPath \ "t").write[String] and
    (JsPath \ "m").write[String]
  )(
    {
      case PrintToConsole(msgType: ConsoleMessageType, msg: String) =>
        (
          "log",
          msgType match {
            case StdOut => "o"
            case StdErr => "e"
          },
          msg
        )
    }: PrintToConsole => (String,String,String)
  )

  implicit val moveRobotWrites: Writes[MoveRobot] = (
    (JsPath \ "c").write[String] and
    (JsPath \ "t").write[Double] and
    (JsPath \ "l").write[Double] and
    (JsPath \ "o").write[Double]
  )(
    {
      case MoveRobot(position: RobotPosition) =>
        ("m", math.round(position.topMm), math.round(position.leftMm), roundOrient(position.orientationRad))
    }: MoveRobot => (String,Double,Double,Double)
  )

  implicit val executeLineWrites: Writes[ExecuteLine] = (
    (JsPath \ "c").write[String] and
    (JsPath \ "l").write[Int]
  )(
    {
      case ExecuteLine(line: Int) => ("l", line)
    }: ExecuteLine => (String,Int)
  )

  implicit val showMessageWrites: Writes[ShowMessage] = (
    (JsPath \ "c").write[String] and
    (JsPath \ "m").write[String]
  )(
    {
      case ShowMessage(message: String) => ("msg", message)
    }: ShowMessage => (String,String)
  )
}
