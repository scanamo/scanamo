package org.scanamo.generic
import org.scanamo.DynamoFormat
import org.scanamo.fixtures._
import org.scanamo.generic.semiauto.deriveDynamoFormat
// dummy for testing
object auto:
  implicit val animalInst:DynamoFormat[Animal] = deriveDynamoFormat[Animal]
  implicit val bearInst: DynamoFormat[Bear] = deriveDynamoFormat[Bear]
  implicit val cityInst:DynamoFormat[City] = deriveDynamoFormat[City]
  implicit val doctorInst:DynamoFormat[Doctor] = deriveDynamoFormat[Doctor]
  implicit val engineInst:DynamoFormat[Engine] = deriveDynamoFormat[Engine]
  implicit val factoryInst:DynamoFormat[Factory] = deriveDynamoFormat[Factory]
  implicit val farmInst:DynamoFormat[Farm] = deriveDynamoFormat[Farm]
  implicit val farmerInst:DynamoFormat[Farmer] = deriveDynamoFormat[Farmer]
  implicit val forecastInst:DynamoFormat[Forecast] = deriveDynamoFormat[Forecast]
  implicit val gremlinInst:DynamoFormat[Gremlin] = deriveDynamoFormat[Gremlin]
  implicit val itemInst:DynamoFormat[Item] = deriveDynamoFormat[Item]
  implicit val lemmingInst:DynamoFormat[Lemming] = deriveDynamoFormat[Lemming]
  implicit val rabbitInst:DynamoFormat[Rabbit] = deriveDynamoFormat[Rabbit]
  implicit val stationInst:DynamoFormat[Station] = deriveDynamoFormat[Station]
  implicit val transportInst:DynamoFormat[Transport] = deriveDynamoFormat[Transport]
  implicit val workerInst:DynamoFormat[Worker] = deriveDynamoFormat[Worker]
end auto