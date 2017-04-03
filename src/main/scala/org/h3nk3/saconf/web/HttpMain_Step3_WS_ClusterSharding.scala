package org.h3nk3.saconf.web

import akka.actor.{ActorSystem, PoisonPill}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.Message
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{CoupledTerminationFlow, Flow, Sink, Source}
import org.h3nk3.saconf.backend.DroneShadow
import org.h3nk3.saconf.domain._

import org.h3nk3.saconf.domain.JsonDomain._

object HttpMain_Step3_WS_ClusterSharding extends App 
  with Directives with OurOwnWebSocketSupport {
  
  implicit val system = ActorSystem("SAConfBackend")
  implicit val materializer = ActorMaterializer()
  implicit val dispatcher = system.dispatcher

  val log = Logging(system, getClass)
  
  Http().bindAndHandle(routes, "127.0.0.1", 8080)

  val drone = ClusterSharding(system).start(
    typeName = DroneShadow.DroneName,
    entityProps = DroneShadow.props(),
    settings = ClusterShardingSettings(system),
    extractEntityId = DroneShadow.extractEntityId,
    extractShardId = DroneShadow.extractShardId
  )
  

  // format: OFF
  def routes =
    path("drone" / DroneId) { droneId =>
      log.info("Accepted websocket connection from Drone: [{}]", droneId)
      handleWebSocketMessages(
        CoupledTerminationFlow.fromSinkAndSource(
          in = Flow[Message].via(conversion).to(Sink.actorRef(drone, onCompleteMessage = PoisonPill)),
          out = Source.maybe[Message]
        )
      )
    }
  // format: ON
  
  def DroneId = Segment
  
  def conversion: Flow[Message, DroneData, Any] =
    Flow[Message].flatMapConcat(_.asBinaryMessage.getStreamedData)
      .mapAsync(1)(t => Unmarshal(t).to[DroneData])
}
