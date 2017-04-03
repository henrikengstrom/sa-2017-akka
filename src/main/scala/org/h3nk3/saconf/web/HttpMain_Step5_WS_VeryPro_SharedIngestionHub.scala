package org.h3nk3.saconf.web

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.Message
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{CoupledTerminationFlow, Flow, Sink, Source}
import org.h3nk3.saconf.domain.DroneData
import org.h3nk3.saconf.domain.JsonDomain._

object HttpMain_Step5_WS_VeryPro_SharedIngestionHub extends App 
  with Directives with OurOwnWebSocketSupport 
  with SharedIngestionHub {
  
  implicit val system = ActorSystem("SAConfBackend")
  implicit val materializer = ActorMaterializer()
  implicit val dispatcher = system.dispatcher

  val log = Logging(system, getClass)
  
  Http().bindAndHandle(routes, "127.0.0.1", 8080)

  
  // all messages from all incoming connections are merged into this single stream:
  initIngestionHub(Sink.foreach {
    case data: DroneData => println(data)
    case other => println(other)
  })
  
  // format: OFF
  def routes =
    path("drone" / DroneId) { droneId =>
      log.info("Accepted websocket connection from Drone: [{}]", droneId)
      handleWebSocketMessages(
        CoupledTerminationFlow.fromSinkAndSource(
          in = Flow[Message].via(conversion).to(ingestionHub),
          out = Source.maybe[Message]
        )
      )
    }
  // format: ON
  
  type DroneId = String
  def DroneId = Segment
  
  def conversion: Flow[Message, DroneData, Any] =
    Flow[Message].flatMapConcat(_.asBinaryMessage.getStreamedData)
      .mapAsync(1)(t => Unmarshal(t).to[DroneData])
}
