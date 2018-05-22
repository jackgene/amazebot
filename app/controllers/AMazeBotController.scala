package controllers

import java.lang.Long.parseUnsignedLong
import java.net.URLDecoder

import actors.SimulationSessionActor
import models.Maze
import play.api.Play.current
import play.api.libs.json.JsValue
import play.api.mvc._

import scala.util.Random

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
  *
  * @author Jack Leow
  * @since August 2017
 */
object AMazeBotController extends Controller {
  /**
   * The home page.
   */
  def index() = Action { implicit request: Request[AnyContent] =>
    val lastAttemptedPathOpt: Option[String] =
      request.cookies.get("lastAttempted").
      map { cookie => URLDecoder.decode(cookie.value, "UTF-8") }

    Redirect(lastAttemptedPathOpt.getOrElse("/maze/level0"))
  }

  /**
   * A maze.
   */
  def maze(name: String) = Action { implicit request: Request[AnyContent] =>
    if (!Maze.byName.contains(name)) NotFound
    else Ok(views.html.index())
  }

  /**
    * A random maze with the given seed, or generate a new seed if not provided.
    */
  def randomMaze(id: Option[String]) = Action { implicit request: Request[AnyContent] =>
    id match {
      case Some(seed: String) => Ok(views.html.index())
      case None => Redirect(s"/maze/random?id=${Random.nextLong().toHexString}")
    }
  }

  /**
    * Creates a simulation session.
    */
  def mazeSimulation(name: String) = WebSocket.acceptWithActor[JsValue,JsValue] { _ => webSocketOut =>
    SimulationSessionActor.props(webSocketOut, Maze.byName(name))
  }

  /**
    * Creates a simulation session for a random maze.
    */
  def randomMazeSimulation(seed: String) = WebSocket.acceptWithActor[JsValue,JsValue] { _ => webSocketOut =>
    SimulationSessionActor.props(
      webSocketOut,
      Maze.random(6, 6, new Random(parseUnsignedLong(seed, 16)))
    )
  }

  /**
    * Java code template.
    */
  def codeTemplate(name: String, ext: String) = Action { implicit request: Request[AnyContent] =>
    Ok.sendResource(
      s"public/templates/${name}.${ext}" match {
        case templatePath if getClass.getClassLoader.getResource(templatePath) != null => templatePath
        case _ => s"public/templates/_default.${ext}"
      }
    )
  }
}
