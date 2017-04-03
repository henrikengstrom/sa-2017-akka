package org.h3nk3.saconf.web

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest}
import akka.http.scaladsl.server.Directives
import akka.stream.{ActorMaterializer, ThrottleMode}
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import com.typesafe.config.ConfigFactory
import org.h3nk3.saconf.domain._

import scala.concurrent.duration._
import scala.concurrent.{Await, Promise}

object HttpMain_Step1_CsvMetrics_Client extends App
  with Directives with OurOwnWebSocketSupport
  with CsvDomain {

  implicit val system = ActorSystem("SAConfBackend", ConfigFactory.parseString(""))
  implicit val materializer = ActorMaterializer()
  implicit val dispatcher = system.dispatcher

  val example =
    DroneData(
      id = 1337,
      status = Operating,
      position = Position(2.0, 5.0),
      velocity = 1.0,
      direction = 90,
      batteryPower = 30
    )

  
  /*
              netstat -n -p tcp | grep 127 | grep 8080
   */

  val dataSource = Source.repeat(NotUsed)
    .mapAsync(1)(_ => Marshal(example).to[ByteString])
    .intersperse(ByteString("\n"))
    .throttle(1000, 1.second, 100, ThrottleMode.Shaping)
    .map { it =>
      println("> " + System.currentTimeMillis())
      it
    }
  
  Http().singleRequest(
    HttpRequest(HttpMethods.POST, uri = "http://127.0.0.1:8080/drone/csv-example")
      .withEntity(HttpEntity(ContentTypes.`text/csv(UTF-8)`, dataSource))
  )

}
