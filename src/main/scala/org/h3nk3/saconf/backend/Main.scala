package org.h3nk3.saconf.backend

import akka.actor.{ActorIdentity, ActorPath, ActorRef, ActorSystem, Identify, PoisonPill, Props}
import akka.cluster.sharding.ClusterSharding
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings, ClusterSingletonProxy, ClusterSingletonProxySettings}
import akka.pattern.ask
import akka.persistence.journal.leveldb.{SharedLeveldbJournal, SharedLeveldbStore}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.h3nk3.saconf.backend.DroneManager.{Initiate, StopDrones, SurveillanceArea}
import org.h3nk3.saconf.domain._

import scala.annotation.tailrec
import scala.concurrent.duration._
import scala.io.StdIn

object Main extends InputParser {
  
  var systems = Seq.empty[ActorSystem]
  var droneManagerProxy: ActorRef = null

  def main(args: Array[String]): Unit = {

    if (args.isEmpty) {
      systems = Seq(2551, 2552) map { startBackend(_) }
    } else {
      val port = args(0).toInt
      systems = Seq(startBackend(port))
    }

    droneManagerProxy = systems.head.actorOf(ClusterSingletonProxy.props("/user/droneManager", ClusterSingletonProxySettings(systems.head)), "droneManagerProxy")
    println(s"Backend server running\nType 'e|exit' to quit the application. Type 'h|help' for information.")
    commandLoop()
  }

  @tailrec
  def commandLoop(): Unit = {
    Cmd(StdIn.readLine()) match {
      case Cmd.Initiate =>
        println("Initiating application...")
        initiate()
        commandLoop()
      case Cmd.Stop =>
        println("Stopping application...")
        stop()
        commandLoop()
      case Cmd.Help =>
        println("Available commands:")
        println("i: Initiate application")
        println("s: Stop application")
        println("h: Help")
        println("e: Exit")
        commandLoop()
      case Cmd.Unknown(s) =>
        println(s"Unknown command: $s")
        commandLoop()
      case Cmd.Exit =>
        println("Exiting application. Bye!")
        systems foreach { _.terminate() }
    }
  }

  def startBackend(port: Int): ActorSystem = {
    val conf = ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$port").withFallback(ConfigFactory.load())
    val system = ActorSystem("SAConfBackend", conf)
    bootstrap(system, port == 2551) // only start store
    system
  }

  def bootstrap(system: ActorSystem, startThings: Boolean): Unit = {
    if (startThings) {
      system.actorOf(
        ClusterSingletonManager.props(
          singletonProps = DroneManager.props,
          terminationMessage = PoisonPill,
          settings = ClusterSingletonManagerSettings(system)),
        "droneManager")
    }

    DroneShadow.startSharding(system)
    
    // Start the shared local journal used in this demo
    startupSharedJournal(system, startThings, ActorPath.fromString("akka.tcp://SAConfBackend@127.0.0.1:2551/user/store"))
  }

  def startupSharedJournal(system: ActorSystem, startStore: Boolean, path: ActorPath): Unit = {
    // Start the shared journal one one node (don't crash this SPOF)
    // This will not be needed with a distributed journal
    if (startStore) system.actorOf(Props[SharedLeveldbStore], "store")

    // register the shared journal
    import system.dispatcher
    implicit val timeout = Timeout(15.seconds)
    val f = system.actorSelection(path) ? Identify(None)
    f.onSuccess {
      case ActorIdentity(_, Some(ref)) => SharedLeveldbJournal.setStore(ref, system)
      case _ =>
        system.log.error("Shared journal not started at {}", path)
        system.terminate()
    }
    f.onFailure {
      case _ =>
        system.log.error("Lookup of shared journal at {} timed out", path)
        system.terminate()
    }
  }

  private def initiate(): Unit = {
    val saConf = systems.head.settings.config.getConfig("saconf.surveillance-area")

    // Instructs the DM what area to perform surveillance on and how many drones to expect
    droneManagerProxy ! Initiate(
      SurveillanceArea(
        Position(saConf.getDouble("upper-left-lat"), saConf.getDouble("upper-left-long")),
        Position(saConf.getDouble("lower-right-lat"), saConf.getDouble("lower-right-long"))),
      systems.head.settings.config.getInt("saconf.number-of-drones")
    )
  }

  private def stop(): Unit = {
    droneManagerProxy ! StopDrones
  }

  private def addDrone(): Unit = 
    ClusterSharding(systems.head).shardRegion(DroneShadow.DroneName) ! DroneShadow.InitDrone
}
