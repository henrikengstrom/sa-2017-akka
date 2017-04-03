package org.h3nk3.saconf.domain

import akka.event.Logging
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.{Marshaller, Marshalling}
import akka.http.scaladsl.model.ws
import akka.http.scaladsl.unmarshalling.Unmarshaller
import org.h3nk3.saconf.backend.DroneManager.SurveillanceArea
import spray.json.{DefaultJsonProtocol, JsObject, JsString, JsValue, JsonReader, JsonWriter, RootJsonFormat}

import scala.concurrent.Future

// object JsonDomain extends Domain with FailFastCirceSupport with AutoDerivation // explain trade-off
trait JsonDomain extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val DroneStatusFormat: RootJsonFormat[DroneStatus] = new RootJsonFormat[DroneStatus] {
    override def read(json: JsValue): DroneStatus = {
      json.asJsObject.fields("status") match {
        case JsString(name) => DroneStatus.fromString(name)
      }
    }
    override def write(obj: DroneStatus): JsValue =
      JsObject("status" -> JsString(obj.toString))
  }

  implicit val DronePositionFormat = jsonFormat2(Position)
  implicit val DroneCommandFormat = new RootJsonFormat[DroneCommand] {
    override def read(json: JsValue): DroneCommand = {
      val o = json.asJsObject
      o.fields("type") match {
        case JsString("SurveilArea") => SurveilArea(
          area = {
            val a = o.fields("area").asJsObject
            SurveillanceArea(
              DronePositionFormat.read(a.fields("upperLeft")),
              DronePositionFormat.read(a.fields("lowerRight"))
            )
          } 
        )
      }
    }
    override def write(obj: DroneCommand): JsValue = {
      obj match {
        case SurveilArea(area) =>
          JsObject(
            "type" -> JsString(Logging.simpleName(obj.getClass)),
            "area" -> JsObject(
              "upperLeft" -> DronePositionFormat.write(area.lowerLeft),
              "lowerRight" -> DronePositionFormat.write(area.upperRight)
            )
          )
      }
    }
  }
  implicit val DroneDataFormat = jsonFormat6(DroneData)
  implicit val InitializeClientFormat = jsonFormat1(InitializeClient)
  
  implicit def MessagesUnmarshalling[T](implicit reader: JsonReader[T]): Unmarshaller[ws.Message, T] =
    Unmarshaller.withMaterializer { implicit ec => implicit mat => value =>  
      value.asTextMessage.asScala.textStream.runFold("")(_ + _) flatMap { acc =>
        import spray.json._
        Future.successful(reader.read(acc.parseJson))
      }
    }
  implicit def MessagesMarshalling[T](implicit writer: JsonWriter[T]): Marshaller[T, ws.Message] =
    Marshaller[T, ws.Message] { implicit ec => value =>  
        Future.successful(Marshalling.Opaque { () =>
          ws.TextMessage(writer.write(value).prettyPrint)
        } :: Nil)
      }
} 
object JsonDomain extends JsonDomain

