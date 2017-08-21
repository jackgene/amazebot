package controllers

import actors.SimulationSessionActor
import models.Maze
import play.api.Play.current
import play.api.libs.json.JsValue
import play.api.mvc._

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
  *
  * @author Jack Leow
  * @since August 2017
 */
object RoombaSimulatorController extends Controller {
  /**
   * The home page.
   */
  def index() = Action { implicit request: Request[AnyContent] =>
    Redirect("/maze/level0") // TODO Read last attempted from cookie
  }

  /**
   * A maze.
   */
  def maze(name: String) = Action { implicit request: Request[AnyContent] =>
    if (!Maze.byName.contains(name)) NotFound
    else Ok(views.html.index())
  }

  /**
    * Creates a simulation session.
    */
  def simulation(name: String) = WebSocket.acceptWithActor[String,JsValue] { request => webSocketOut =>
    SimulationSessionActor.props(webSocketOut, Maze.byName(name))
  }
}
