package org.h3nk3.saconf

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.Await
import scala.concurrent.duration._

abstract class AkkaSpec(config: Config) extends TestKit(ActorSystem("TestActorSystem", config))
  with WordSpecLike with Matchers
  with ImplicitSender
  with BeforeAndAfterAll 
  with ScalaFutures {
  
  implicit def materializer: Materializer = ActorMaterializer()
  implicit def executionContext = system.dispatcher
  
  
  def this(config: String) {
    this(ConfigFactory.parseString(config).withFallback(ConfigFactory.load()))
  }
  
  def this() {
    this(ConfigFactory.parseString(""))
  }
  
  override def afterAll(): Unit = {
    super.afterAll()
    Await.ready(system.terminate(), 3.seconds)
  }
}
