package org.h3nk3.saconf.drone

import akka.NotUsed
import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{HttpEntity, ws}
import akka.http.scaladsl.model.ws._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{ActorAttributes, ActorMaterializer}
import com.typesafe.config.ConfigFactory
import org.h3nk3.saconf.backend.InputParser
import org.h3nk3.saconf.domain.{JsonDomain, _}

import scala.annotation.tailrec
import scala.concurrent.duration._
import scala.io.StdIn
import scala.util.Random

object Drone extends InputParser with JsonDomain {

  implicit val sys = ActorSystem("Drone-" + System.currentTimeMillis(), ConfigFactory.load("drone-client.conf"))
  implicit val mat = ActorMaterializer()
  val log = Logging(sys, getClass)
  import sys.dispatcher

  private val startTime: Long = System.currentTimeMillis()
  private val basePosition: Position = Position(sys.settings.config.getDouble("saconf.client.base.lat"), sys.settings.config.getDouble("saconf.client.base.long"))
  private val maxVelocity: Double = sys.settings.config.getDouble("saconf.client.max-velocity")
  private val xCoordinates: Int = sys.settings.config.getInt("saconf.client.x-coordinates")
  private val yCoordinates: Int = sys.settings.config.getInt("saconf.client.y-coordinates")
  private var currentPosition = 0

  // Explain why this might need to be protected...
  private var lowerLeft: Option[Position] = None
  private var upperRight: Option[Position] = None
  private var incrementalLatDistance: Double = 0.0
  private var incrementalLongDistance: Double = 0.0


  var droneId: Int = 0

  def main(args: Array[String]): Unit = {
    if (args.isEmpty) {
      println(Console.RED + "**** You must provide a drone id when starting the drone client. ****" + Console.RESET)
      System.exit(0)
    } else {
      droneId = args.head.toInt
      bootstrap()
      println(
        Console.GREEN + 
        s"*** Drone client [$droneId running]\n" +
        s"Type 'e|exit' to quit the application. Type 'h|help' for information." + 
        Console.RESET)
      commandLoop()
    }
  }

  @tailrec
  def commandLoop(): Unit = {
    Cmd(StdIn.readLine()) match {
      case Cmd.Help =>
        println("Available commands:")
        println("h: Help")
        println("e: Exit")
        commandLoop()
      case Cmd.Unknown(s) =>
        println(s"Unknown command: $s")
        commandLoop()
      case Cmd.Exit =>
        println("Exiting application. Bye!")
        sys.terminate()
      case Cmd.Initiate =>
        // ignore
        commandLoop()
      case Cmd.Stop =>
        // ignore
        commandLoop()
    }
  }

  def bootstrap(): Unit = {
    val url = s"${sys.settings.config.getString("saconf.http-server")}drone/$droneId"
    log.info("Connecting to: {}", url)
    Http().singleWebSocketRequest(
      WebSocketRequest(url),
      clientFlow = emitPositionAndMetrics)
  }

  def handleCommand(json: ws.Message): Unit = {
    Unmarshal(json).to[DroneCommand] map {
      case sa @ SurveilArea(area) =>
        this.lowerLeft = Some(area.lowerLeft)
        this.upperRight = Some(area.upperRight)
        incrementalLatDistance = (area.upperRight.lat - area.lowerLeft.lat) / xCoordinates
        incrementalLongDistance = (area.upperRight.long - area.lowerLeft.long) / yCoordinates
    }
  }

  def emitPositionAndMetrics: Flow[Message, Message, Any] =
    // Explain: Coupled vs Flow
    Flow.fromSinkAndSource(
      Sink.foreach(handleCommand),
      Source.tick(initialDelay = 1.second, interval = 3.seconds, tick = NotUsed)
        .map(_ => getCurrentInfo)
        .via(renderAsJson)
        .log(s"Drone-$droneId").withAttributes(ActorAttributes.logLevels(onElement = Logging.InfoLevel))
        .map(TextMessage(_))
    )

  def getCurrentInfo: DroneData = {
    def position(): Position = {
      val latPos = (currentPosition % xCoordinates) * incrementalLatDistance
      val longPos = (currentPosition / yCoordinates) * incrementalLongDistance
      currentPosition += 1
      Position(latPos, longPos)
    }

    def velocity(): Double = {
      maxVelocity - Random.nextDouble() // let the velocity fluctuate a bit (depending on winds)
    }

    def direction: Int =
      if ((currentPosition / yCoordinates) % 2 == 0) 90
      else 270

    def batteryPower: Int = {
      100 - ((System.currentTimeMillis() - startTime) / 60000).toInt // drain 1 % per minute
    }

    val data =
      if (upperRight.isDefined) DroneData(droneId, Operating, position(), velocity, direction, batteryPower)
      else DroneData(droneId, Ready, Position(0.0, 0.0), 0.0, 0, 100)

    println(s"> $data")
    data
  }

  val renderAsJson: Flow[DroneData, String, NotUsed] =
    Flow[DroneData]
      .mapAsync(parallelism = 1)(p => Marshal(p).to[HttpEntity].flatMap(_.toStrict(1.seconds)))
      .map(_.data.utf8String)

}
