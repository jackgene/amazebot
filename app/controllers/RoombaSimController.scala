package controllers

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

  private val ClassNameExtractor = "(?s).*public class ([A-Za-z][A-Za-z0-9]+).*".r
  /**
    * Runs the simulation.
    */
  def runSimulation() = Action { implicit request: Request[AnyContent] =>
    Ok(
      Json.toJson(
        for {
          steps: Seq[Operation] <- (
            for (codeBlock: String <- request.body.asText) yield {
              val ClassNameExtractor(className: String) = codeBlock
              val compiler = CompilerFactoryFactory.getDefaultCompilerFactory.newSimpleCompiler()
              compiler.cook(codeBlock)

              val classLoader = compiler.getClassLoader
              val controllerClass = classLoader.loadClass(className)
              val runMethod = controllerClass.getMethod("run", classOf[Robot])

              val robot = new Robot
              runMethod.invoke(null, robot)

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
