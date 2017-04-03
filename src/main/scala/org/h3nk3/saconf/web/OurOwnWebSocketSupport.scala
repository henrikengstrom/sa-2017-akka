package org.h3nk3.saconf.web

import akka.NotUsed
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.model.ws.TextMessage.{Streamed, Strict}
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink}

import scala.concurrent.Future
import scala.util.Random

trait OurOwnWebSocketSupport {

  implicit def materializer: Materializer
  
  val websocketEcho: Flow[Message, Message, Any] =
    Flow[Message]
      .via(toStrictText)
      .map { text =>
        Thread.sleep(Random.nextInt(100))
        text
      }

  // TODO this should be in Akka itself actually, has tickets
  def toStrictText(implicit mat: Materializer): Flow[Message, TextMessage.Strict, NotUsed] = {
    Flow[Message]
      .map(_.asTextMessage)
      .mapAsync(1) {
        case t: Streamed => t.textStream.runFold("")(_ ++ _)(materializer)
        case t: Strict => Future.successful(t.getStrictText)
      }
      .map(TextMessage.Strict)
  }

}
