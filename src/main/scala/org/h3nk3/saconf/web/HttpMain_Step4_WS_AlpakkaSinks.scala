package org.h3nk3.saconf.web

import akka.NotUsed
import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.Message
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.Producer
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{CoupledTerminationFlow, Flow, Sink, Source}
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{ByteArraySerializer, StringSerializer}
import org.h3nk3.saconf.domain.DroneData

object HttpMain_Step4_WS_AlpakkaSinks extends App 
  with Directives with OurOwnWebSocketSupport {
  
  implicit val system = ActorSystem("SAConfBackend")
  implicit val materializer = ActorMaterializer()
  implicit val dispatcher = system.dispatcher

  val log = Logging(system, getClass)
  
  Http().bindAndHandle(routes, "127.0.0.1", 8080)

  
  // ------ kafka setup ------
  val producerSettings = ProducerSettings(system, new ByteArraySerializer, new StringSerializer)
    .withBootstrapServers("localhost:9092")
  
  // format: OFF
  def routes =
    path("drone" / DroneId) { droneId =>
      log.info("Accepted websocket connection from Drone: [{}]", droneId)
      handleWebSocketMessages(
        Flow.fromSinkAndSource(
          sink = Flow[Message].via(conversion)
              .to(akka.kafka.scaladsl.Producer.plainSink(producerSettings)),
          source = Source.maybe[Message]
        )
      )
    }
  // format: ON
  
  def DroneId = Segment

  def conversion: Flow[Message, ProducerRecord[Array[Byte], String], NotUsed] =
    Flow[Message]
      .flatMapConcat(_.asTextMessage.getStreamedText)
      .map { payload =>
        new ProducerRecord[Array[Byte], String]("topic1", payload)
      }
  
}
