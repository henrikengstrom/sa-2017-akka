package org.h3nk3.saconf.web

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpMethods, HttpRequest}
import akka.http.scaladsl.server.Directives
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import org.h3nk3.saconf.domain.{CsvDomain, DroneData}

import scala.concurrent.duration._
import scala.concurrent.{Await, Promise}

object HttpMain_Step1_CsvMetrics extends App 
  with Directives with OurOwnWebSocketSupport 
  with CsvDomain {

  implicit val system = ActorSystem("SAConfBackend")
  implicit val materializer = ActorMaterializer()
  implicit val dispatcher = system.dispatcher

  // format: OFF
  def routes =
    path("drone" / Segment) { droneId => 
      entity(asSourceOf[DroneData]) { infos =>
        complete {
          infos.runWith(Sink.ignore).map { done =>
            "consumed all data!"
          }
        }
      }
    }

  // format: ON


  val binding = Http().bindAndHandle(routes, "127.0.0.1", 8080)

  Await.ready(binding, 5.seconds)

}
