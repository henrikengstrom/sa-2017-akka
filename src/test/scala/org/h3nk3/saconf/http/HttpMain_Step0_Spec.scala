package org.h3nk3.saconf.http

import akka.http.scaladsl.model.{HttpMethod, HttpMethods, StatusCodes}
import akka.http.scaladsl.server.MethodRejection
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.h3nk3.saconf.AkkaSpec
import org.h3nk3.saconf.web.HttpMain_Step0_HelloWorld
import org.h3nk3.saconf.web.HttpMain_Step0_HelloWorld.{complete, get}
import org.scalatest.{FlatSpec, Matchers, WordSpec}

class HttpMain_Step0_Spec extends WordSpec with ScalatestRouteTest with Matchers {

  val routes = HttpMain_Step0_HelloWorld.routes
  
  "Hello World route" should {
    "GET / result in OK" in {
      Get() ~> routes ~> check {
        status should === (StatusCodes.OK)
        responseAs[String].trim should ===("Hello world!")
      }
    }

    "POST / result in 404" in {
      Post() ~> routes ~> check {
        rejection should === (MethodRejection(supported = HttpMethods.GET))
      }
    }
  }
}
