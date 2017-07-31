package controllers

import javax.inject._

import models.Robot
import models.Robot.{MoveForward, Operation, TurnRight}
import org.codehaus.commons.compiler.CompilerFactoryFactory
import play.api.libs.json.Json
import play.api.mvc._

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
object RoombaSimController extends Controller {

  /**
   * Create an Action to render an HTML page.
   *
   * The configuration in the `routes` file means that this method
   * will be called when the application receives a `GET` request with
   * a path of `/`.
   */
  def index() = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.index())
  }

  /**
    * Runs the simulation.
    */
  def runSimulation() = Action { implicit request: Request[AnyContent] =>
    Ok(
      Json.toJson(
        for {
          steps: Seq[Operation] <- (
            for (codeBlock: String <- request.body.asText) yield {
              val evaluator = CompilerFactoryFactory.getDefaultCompilerFactory.newScriptEvaluator()
              evaluator.setParameters(Array("robot"), Array[Class[_]](classOf[Robot]))
              evaluator.cook(s"${codeBlock}")

              val robot = new Robot
              evaluator.evaluate(Array(robot))

              robot.steps.reverse
            }
          ).toList
          step: Operation <- steps
        } yield step match {
          case MoveForward => "f"
          case TurnRight => "r"
        }
      )
    )
  }
}
