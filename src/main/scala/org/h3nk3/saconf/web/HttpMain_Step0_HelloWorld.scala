package org.h3nk3.saconf.web

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives
import akka.stream.ActorMaterializer


object HttpMain_Step0_HelloWorld extends App 
  with Directives with OurOwnWebSocketSupport { 

  implicit val system = ActorSystem("SAConfBackend")
  implicit val materializer = ActorMaterializer()
  implicit val dispatcher = system.dispatcher

  def routes =
    get {
      complete("Hello world!")
    }

  Http().bindAndHandle(routes, "127.0.0.1", 8080)
}
