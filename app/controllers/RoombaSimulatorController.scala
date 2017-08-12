package controllers

import actors.SimulationSessionActor
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
    Ok(views.html.index())
  }

  /**
    * Creates a simulation session.
    */
  def simulation() = WebSocket.acceptWithActor[String,JsValue] { request => webSocketOut =>
    SimulationSessionActor.props(webSocketOut)
  }
}
