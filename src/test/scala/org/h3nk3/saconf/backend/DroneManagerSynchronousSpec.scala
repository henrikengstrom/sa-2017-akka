package org.h3nk3.saconf.backend

import akka.actor.ActorSystem
import akka.testkit.{TestActorRef, TestKit}
import com.typesafe.config.ConfigFactory
import org.h3nk3.saconf.backend.DroneManager.SurveillanceArea
import org.h3nk3.saconf.domain.Position
import org.scalatest.{Matchers, WordSpecLike}

class DroneManagerSynchronousSpec 
  extends TestKit(ActorSystem("TestActorSystem", ConfigFactory.parseString(""))) 
  with WordSpecLike with Matchers {

  "DroneManager" should {
    "divide surveillance area based on number of drones" in {
      val droneManagerActor = TestActorRef[DroneManager].underlyingActor

      val upperLeftDronePosition = Position(0.0, 0.0)
      val lowerRightDronePosition = Position(10.0, 10.0)
      val sa = SurveillanceArea(upperLeftDronePosition, lowerRightDronePosition)
      val areas1 = droneManagerActor.divideAreas(sa, 1)
      val areas2 = droneManagerActor.divideAreas(sa, 2)
      val areas4 = droneManagerActor.divideAreas(sa, 4)

      areas1.size should be(1)
      areas2.size should be(2)
      areas4.size should be(4)

      areas1.head should equal(SurveillanceArea(upperLeftDronePosition, lowerRightDronePosition))

      areas2 should contain(SurveillanceArea(Position(0.0, 0.0), Position(5.0, 10.0)))
      areas2 should contain(SurveillanceArea(Position(5.0, 0.0), Position(10.0, 10.0)))

      areas4 should contain(SurveillanceArea(Position(5.0, 0.0), Position(7.5, 10.0)))
      areas4 should contain(SurveillanceArea(Position(7.5, 0.0), Position(10.0, 10.0)))
    }
  }
}

