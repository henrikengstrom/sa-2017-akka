package org.h3nk3.saconf.domain

import org.h3nk3.saconf.backend.DroneManager.SurveillanceArea

/**
 * Always use as: `import org.h3nk3.saconf._`
 * in order to enable automatic marshalling via Circe.
 */

  case class Base(id: String, position: Position)

  case class DroneCommandError(string: String)
  
  // Events
  trait SAConfEvent

  case class InitializeClient(clientId: String) extends SAConfEvent
  case class DroneData(id: Int, status: DroneStatus, position: Position, velocity: Double, direction: Int, batteryPower: Int) extends Serializable

  trait DroneStatus extends Serializable
  object DroneStatus {
    def fromString(s: String): DroneStatus = s match {
      case "Charging"    => Charging
      case "Ready"       => Ready
      case "Operating"   => Operating
      case "Maintenance" => Maintenance
      case "Stopped"     => Stopped
    }
  }
  case object Charging    extends DroneStatus
  case object Ready       extends DroneStatus
  case object Operating   extends DroneStatus
  case object Maintenance extends DroneStatus
  case object Stopped     extends DroneStatus

  /** Commands set to the field-deployed Drones */
  trait DroneCommand
  final case class SurveilArea(area: SurveillanceArea) extends DroneCommand

  final case class Position(lat: Double, long: Double) extends Serializable
