package org.h3nk3.saconf.web

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.Message
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow
import akka.util.Timeout
import org.h3nk3.saconf.backend.DroneConnectionHub
import org.h3nk3.saconf.domain._
import org.h3nk3.saconf.domain.JsonDomain._

import scala.concurrent.duration._

object HttpMain_Step2_WebSocket extends App 
  with Directives with OurOwnWebSocketSupport {

  implicit val system = ActorSystem("SAConfBackend")
  implicit val materializer = ActorMaterializer()
  implicit val dispatcher = system.dispatcher
  implicit val timeout = Timeout(10.second)

  val log = Logging(system, getClass)
  
  Http().bindAndHandle(routes, "127.0.0.1", 8080)

  val droneConnectionHub = system.actorOf(DroneConnectionHub.props())
  
  // format: OFF
  def routes =
    path("drone" / DroneId) { droneId =>
      log.info("Accepted websocket connection from Drone: [{}]", droneId)
      val reply = droneConnectionHub ? DroneConnectionHub.DroneArrive(droneId)
      onSuccess(reply.mapTo[DroneConnectionHub.DroneHandler]) { handler =>
        handleWebSocketMessages(handler.flow)
      }
    }
  // format: ON
  
  def DroneId = Segment
  
  def conversion: Flow[Message, DroneData, Any] =
    Flow[Message].flatMapConcat(_.asBinaryMessage.getStreamedData)
      .mapAsync(1)(bs => Unmarshal(bs).to[DroneData])
}
