package org.h3nk3.saconf.backend

import akka.NotUsed
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Deploy, PoisonPill, Props, Terminated}
import akka.event.Logging
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.ws
import akka.http.scaladsl.model.ws.Message
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.scaladsl.{CoupledTerminationFlow, Flow, Keep, Sink, Source, SourceQueueWithComplete}
import akka.stream.{ActorAttributes, ActorMaterializer, OverflowStrategy}
import org.h3nk3.saconf.domain.{DroneCommand, DroneData}

object DroneConnectionHub {
  
  def name = "droneConnectionHub"
  
  def props(): Props =
    Props(new DroneConnectionHub).withDeploy(Deploy.local)
  
  case class DroneArrive(id: String)
  case class DroneAway(id: String)
  
  /** Once registered here, we are able to handle and send messages to the field-deployed Drone */
  case class DroneHandler(flow: Flow[ws.Message, ws.Message, _])
}

/** Manages connections and pushes commands to field-deployed Drones */
class DroneConnectionHub extends Actor with ActorLogging {
  import DroneConnectionHub._
  import org.h3nk3.saconf.domain.JsonDomain._

  val DroneShadowsShard = DroneShadow.startSharding(context.system)
  
  implicit val mat = ActorMaterializer()
  import context.dispatcher
  
  var DroneOut = Map.empty[String, ActorRef]
  
  override def receive: Receive = {
    case DroneArrive(droneId) =>
      log.info("Opening control connection with Drone [{}]", droneId)
      val (toDroneRef, toDronePublisher) =
        Source.actorRef[DroneCommand](128, OverflowStrategy.dropHead)
          .mapAsync(parallelism = 1)(cmd => Marshal(cmd).to[ws.Message])
          .toMat(Sink.asPublisher(false))(Keep.both).run()
      
      val outToDrone: Source[Message, NotUsed] = Source.fromPublisher(toDronePublisher)
      
      val inFromDrone = Flow[ws.Message]
        .mapAsync(parallelism = 1)(msg => Unmarshal(msg).to[DroneData])
          .log(s"from-drone-id: $droneId").withAttributes(ActorAttributes.logLevels(onElement = Logging.WarningLevel))
        .to(Sink.foreach(data => DroneShadowsShard ! data)) 
      
      DroneOut = DroneOut.updated(droneId, toDroneRef)
      
      sender() ! DroneHandler(
        CoupledTerminationFlow.fromSinkAndSource(inFromDrone, outToDrone)
      )

    case DroneAway(id) =>
      log.info("Removing drone client [{}]", id)
      DroneOut = DroneOut - id

    case DroneShadow.RequestDroneConnection =>
      log.info("Offering field-deployed Drone connection to {}", sender())
      context.watch(sender())
      sender() ! DroneShadow.DroneConnection(DroneOut(sender().path.name))

    case Terminated(ref) =>
      val id = ref.path.name
      log.warning("Backend Drone actor [{}] terminated. Out connection still available: {}", id, DroneOut.get(id).isDefined)
  }
}
