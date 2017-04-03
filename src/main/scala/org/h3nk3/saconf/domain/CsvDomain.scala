package org.h3nk3.saconf.domain

import akka.http.scaladsl.common.EntityStreamingSupport
import akka.http.scaladsl.marshalling.{Marshaller, Marshalling}
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.util.ByteString

import scala.concurrent.Future

trait CsvDomain {
  implicit val csvStreaming = EntityStreamingSupport.csv()

  implicit val csvUnmarshalling: Unmarshaller[ByteString, DroneData] =
    Unmarshaller.strict { bs =>
      val it = bs.utf8String.split(",").iterator
      import it.next
      DroneData(
        next().toInt,
        DroneStatus.fromString(next()),
        Position(next().toDouble, next().toDouble),
        next().toDouble,
        next().toInt,
        next().toInt
      )
    }
  
  implicit val csvMarshalling: Marshaller[DroneData, ByteString] =
    Marshaller { implicit ec => data =>
      Future {
        Marshalling.WithFixedContentType(ContentTypes.`text/csv(UTF-8)`, marshal = () => {

          val sb = new StringBuilder
          sb.append(data.id)
          sb.append(",")
          sb.append(data.status)
          sb.append(",")
          sb.append(data.position.lat)
          sb.append(",")
          sb.append(data.position.long)
          sb.append(",")
          sb.append(data.velocity)
          sb.append(",")
          sb.append(data.direction)
          sb.append(",")
          sb.append(data.batteryPower)
          ByteString(sb.result())
        }) :: Nil
      }
    }

}

object CsvDomain extends CsvDomain {

} 
