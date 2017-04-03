package org.h3nk3.saconf.backend

import akka.actor.{ActorLogging, ActorRef, ActorSystem, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import akka.cluster.singleton.{ClusterSingletonProxy, ClusterSingletonProxySettings}
import akka.persistence.PersistentActor
import org.h3nk3.saconf.backend.DroneManager.SurveillanceArea
import org.h3nk3.saconf.domain._

object DroneShadow {
  def props(): Props = Props[DroneShadow]

  final val DroneName = "Drone"

  case class DroneInitData(surveillanceArea: SurveillanceArea) extends Serializable
  case object InitDrone extends Serializable
  
  /** Request field-deployed Drone connection via websocket, from source of incoming data. */
  case object RequestDroneConnection
  case class DroneConnection(out: ActorRef)

  sealed trait DroneCommand extends Serializable
  final case object DroneInfoCommand extends DroneCommand

  sealed trait DroneEvent extends Serializable
  final case class DroneDataEvent(id: String, status: DroneStatus, lat: Double, long: Double, velocity: Double, direction: Int, batteryPower: Int, distanceCovered: Double, createdTime: Long = System.currentTimeMillis()) extends DroneEvent
  
  // --- cluster sharding --- 
  
  // Try to produce a uniform distribution, i.e. same amount of entities in each shard.
  // As a rule of thumb, the number of shards should be a factor ten greater than the planned maximum number of cluster nodes.
  private final val NumberOfShards = 100
  
  def extractShardId: ShardRegion.ExtractShardId = {
    case DroneData(id, _, _, _, _, _) => (id % NumberOfShards).toString
  }

  def extractEntityId: ShardRegion.ExtractEntityId = {
    case msg @ DroneData(id, _, _, _, _, _) => (id.toString, msg)
  }
  
  def startSharding(system: ActorSystem): ActorRef =
    ClusterSharding(system).start(
      typeName = DroneShadow.DroneName,
      entityProps = DroneShadow.props(),
      settings = ClusterShardingSettings(system),
      extractEntityId = DroneShadow.extractEntityId,
      extractShardId = DroneShadow.extractShardId
    )
}

class DroneShadow extends PersistentActor with ActorLogging {
  import org.h3nk3.saconf.backend.DroneShadow._

  val droneManageProxy: ActorRef = context.system.actorOf(ClusterSingletonProxy.props("/user/droneManager", ClusterSingletonProxySettings(context.system)))

  override def postStop(): Unit = {
    droneManageProxy ! DroneManager.DroneStopped(self)
  }

  private val id = self.path.name
  override def persistenceId: String = s"Drone-$id"

  var position: Position = Position(0, 0)
  var droneId: Int = 0

  /** Ref that transmits commands to field-deployed Drone via WebSocket messages */
  var DroneCommandOut: Option[ActorRef] = None
  var surveilArea: Option[SurveilArea] = None

  override def preStart(): Unit = droneManageProxy ! DroneManager.DroneStarted(self)

  override def receiveCommand: Receive = {
    case cmd: SurveilArea =>
      log.info(s"Drone: {} initialized with {}", droneId, self.path)

      // sends command via WebSocket to field-deployed Drone
      DroneCommandOut match {
        case None =>
          log.warning(s"Field-deployed Drone [{}] currently not connected! " +
            s"Unable to send command {} to it. Storing the command locally until connection is available.", id, cmd)
          surveilArea = Some(cmd)
        case Some(out) =>
          out forward cmd
      } 

    case DroneData(_, status, pos, velocity, direction, batteryPower) =>
      log.debug("Received DroneData from *live* Drone. Sender = {}", sender())
      ensureFieldDeployedDroneConnection()
        
      val distance = calcDistance(pos)
      val event = DroneDataEvent(id, status, pos.lat, pos.long, velocity, direction, batteryPower, distance)
      persist(event)(updateState)

    case DroneShadow.DroneConnection(out) => 
      log.info("Obtained deployed Drone connection. Able to send control commands.")
      DroneCommandOut = Some(out) // we store the out-connection, in order to send Commands to it
      // If the surveil command is already available -> send it to the client
      surveilArea foreach { out forward _ }

  }

  private def ensureFieldDeployedDroneConnection() = {
    if (DroneCommandOut.isEmpty) sender() ! DroneShadow.RequestDroneConnection
  }

  override def receiveRecover: Receive = {
    case dde: DroneDataEvent => updateState(dde)
  }

  val updateState: DroneDataEvent => Unit = {
    case dde: DroneDataEvent =>
      val knownUptime = System.currentTimeMillis() - dde.createdTime
      log.info("Drone data event: " + (dde.id, dde.status, knownUptime, Position(dde.lat, dde.long), dde.distanceCovered))
  }

  private def calcDistance(pos: Position): Double = {
    Math.sqrt(Math.pow(Math.abs(pos.lat - position.lat), 2) + Math.pow(Math.abs(pos.long - position.long), 2))
  }
}
