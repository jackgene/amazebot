package models

object Robot {
  sealed abstract class Operation
  case object MoveForward extends Operation
  case object TurnRight extends Operation
}
class Robot {
  import Robot._

  var steps: List[Operation] = Nil

  def moveForward(): Unit = {
    steps = MoveForward :: steps
  }

  def turnRight() {
    steps = TurnRight :: steps
  }
}
