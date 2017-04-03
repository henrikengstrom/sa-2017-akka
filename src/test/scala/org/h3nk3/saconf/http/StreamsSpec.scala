package org.h3nk3.saconf.http

import akka.http.scaladsl.model.{HttpMethods, StatusCodes}
import akka.http.scaladsl.server.MethodRejection
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.testkit.TestSubscriber.Probe
import akka.stream.testkit.scaladsl.TestSink
import org.h3nk3.saconf.AkkaSpec
import org.h3nk3.saconf.web.HttpMain_Step0_HelloWorld
import org.scalatest.{Matchers, WordSpec}

import scala.collection.immutable.Seq
import scala.concurrent.Future

class StreamsSpec extends AkkaSpec {

  "Example tests" should {
    "Use Sink.seq" in {
      val stream = Source(1 to 10).filter(_ % 2 == 0)
      
      val them: Future[Seq[Int]] = stream.runWith(Sink.seq)
      them.futureValue should equal(List(2, 4, 6, 8, 10))
    }

    "Use very low level testing TestSink.probe" in {
      val stream = Source(1 to 10).filter(_ % 2 == 0)
      
      val probe: Probe[Int] = stream.runWith(TestSink.probe)
      probe.ensureSubscription()
      
      probe.requestNext(2)
      
      probe.request(1)
      probe.expectNext() should === (4)
      
      probe.request(100)
        .expectNext(6, 8, 10)
        .expectComplete()
    }

  }
}
