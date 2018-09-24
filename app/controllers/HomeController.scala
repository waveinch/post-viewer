package controllers

import javax.inject._

import akka.actor._
import akka.stream.Materializer
import play.api._
import play.api.libs.streams.ActorFlow
import play.api.mvc._

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject()(cc: ControllerComponents)(implicit system: ActorSystem, mat: Materializer) extends AbstractController(cc) {


  val supervisor = system.actorOf(Props(new POSTSupervisor))

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


  def socket() = WebSocket.accept[String, String] { request =>
    ActorFlow.actorRef { out =>
      POST2WebSocketActor.props(supervisor,out)
    }
  }

  import POST2WebSocketActor._

  def post() = Action { implicit r =>
    if(r.hasBody) {
      supervisor ! POSTBody(r.body.toString)
    }
    Ok("recived")
  }
}

class POSTSupervisor extends Actor {

  var clients:scala.collection.mutable.Set[ActorRef] = scala.collection.mutable.Set()

  import POST2WebSocketActor._
  override def receive: Receive = {
    case Register(client) => clients.add(client)
    case UnRegister(client) => clients.remove(client)
    case POSTBody(body) => clients.foreach(_ ! body)
  }
}

object POST2WebSocketActor {
  def props(supervisor:ActorRef, out: ActorRef) = Props(new POST2WebSocketActor(supervisor,out))
  case class POSTBody(body:String)
  case class Register(actor:ActorRef)
  case class UnRegister(actor:ActorRef)
}

class POST2WebSocketActor(supervisor:ActorRef,out: ActorRef) extends Actor {
  import POST2WebSocketActor._

  supervisor ! Register(out)

  override def postStop() = {
    supervisor ! UnRegister(out)
  }

  def receive = {
    case _ => {}
  }
}
