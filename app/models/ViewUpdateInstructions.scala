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

  implicit val wallWrites: Writes[Maze.Wall] = (
    (JsPath \ "t").write[Double] and
    (JsPath \ "l").write[Double] and
    (JsPath \ "h").write[Double] and
    (JsPath \ "w").write[Double]
  ) {
    wall: Maze.Wall =>
      (wall.topLeft.topMm, wall.topLeft.leftMm, wall.height, wall.width)
  }

  implicit val drawMazeWrites: Writes[DrawMaze] = (
    (JsPath \ "c").write[String] and
    (JsPath \ "ft").write[Double] and
    (JsPath \ "fl").write[Double] and
    (JsPath \ "w").write[Set[Maze.Wall]]
  )(
    {
      case DrawMaze(finish: Point, walls: Set[Maze.Wall]) =>
        ("maze", finish.topMm, finish.leftMm, walls)
    }: DrawMaze => (String,Double,Double,Set[Maze.Wall])
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
        ("mv", position.topMm, position.leftMm, position.orientationRad)
    }: MoveRobot => (String,Double,Double,Double)
  )

  implicit val showMessageWrites: Writes[ShowMessage] = (
    (JsPath \ "c").write[String] and
    (JsPath \ "m").write[String]
  )(
    {
      case ShowMessage(message: String) =>
        ("msg", message)
    }: ShowMessage => (String,String)
  )
}
