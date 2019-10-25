package org.scanamo

import cats.instances.either._
import cats.syntax.apply._

package object fixtures {

  implicit val animalInstance: DynamoFormat[Animal] =
    DynamoFormat.ObjectFormat.build[Animal](
      o => (o.get[String]("species"), o.get[Int]("number")).mapN(Animal),
      x =>
        DynamoObject(
          "species" -> DynamoValue.fromString(x.species),
          "number" -> DynamoValue.fromNumber(x.number)
        )
    )

  implicit val bearInstance: DynamoFormat[Bear] =
    DynamoFormat.ObjectFormat.build[Bear](
      o => (o.get[String]("name"), o.get[String]("favouriteFood"), o.get[Option[String]]("alias")).mapN(Bear),
      x =>
        DynamoObject(
          "name" -> DynamoValue.fromString(x.name),
          "favouriteFood" -> DynamoValue.fromString(x.favouriteFood)
        ) <> x.alias.fold(DynamoObject.empty)(a => DynamoObject.singleton("alias", DynamoValue.fromString(a)))
    )

  implicit val cityInstance: DynamoFormat[City] =
    DynamoFormat.ObjectFormat.build[City](
      o => (o.get[String]("name"), o.get[String]("country")).mapN(City),
      x =>
        DynamoObject(
          "name" -> DynamoValue.fromString(x.name),
          "country" -> DynamoValue.fromString(x.country)
        )
    )

  implicit val doctorInstance: DynamoFormat[Doctor] =
    DynamoFormat.ObjectFormat.build[Doctor](
      o => (o.get[String]("actor"), o.get[Int]("regeneration")).mapN(Doctor),
      x =>
        DynamoObject(
          "actor" -> DynamoValue.fromString(x.actor),
          "regeneration" -> DynamoValue.fromNumber(x.regeneration)
        )
    )

  implicit val engineInstance: DynamoFormat[Engine] =
    DynamoFormat.ObjectFormat.build[Engine](
      o => (o.get[String]("name"), o.get[Int]("number")).mapN(Engine),
      x =>
        DynamoObject(
          "name" -> DynamoValue.fromString(x.name),
          "number" -> DynamoValue.fromNumber(x.number)
        )
    )

  implicit val factoryInstance: DynamoFormat[Factory] =
    DynamoFormat.ObjectFormat.build[Factory](
      o => (o.get[Int]("id"), o.get[String]("name")).mapN(Factory),
      x =>
        DynamoObject(
          "id" -> DynamoValue.fromNumber(x.id),
          "name" -> DynamoValue.fromString(x.name)
        )
    )

  implicit val farmInstance: DynamoFormat[Farm] =
    DynamoFormat.ObjectFormat.build[Farm](
      o => o.get[List[String]]("asyncAnimals").map(Farm),
      x =>
        DynamoObject(
          "asyncAnimals" -> DynamoValue.fromStrings(x.asyncAnimals)
        )
    )

  implicit val farmerInstance: DynamoFormat[Farmer] =
    DynamoFormat.ObjectFormat.build[Farmer](
      o => (o.get[String]("name"), o.get[Long]("age"), o.get[Farm]("farm")).mapN(Farmer),
      x =>
        DynamoObject(
          "name" -> DynamoValue.fromString(x.name),
          "age" -> DynamoValue.fromNumber(x.age),
          "farm" -> farmInstance.write(x.farm)
        )
    )

  implicit val forecastInstance: DynamoFormat[Forecast] =
    DynamoFormat.ObjectFormat.build[Forecast](
      o => (o.get[String]("location"), o.get[String]("weather"), o.get[Option[String]]("equipment")).mapN(Forecast),
      x =>
        DynamoObject(
          "location" -> DynamoValue.fromString(x.location),
          "weather" -> DynamoValue.fromString(x.weather)
        ) <> x.equipment.fold(DynamoObject.empty)(e => DynamoObject.singleton("equipment", DynamoValue.fromString(e)))
    )

  implicit val gremlinInstance: DynamoFormat[Gremlin] =
    DynamoFormat.ObjectFormat.build[Gremlin](
      o => (o.get[Int]("number"), o.get[Boolean]("wet")).mapN(Gremlin),
      x =>
        DynamoObject(
          "number" -> DynamoValue.fromNumber(x.number),
          "wet" -> DynamoValue.fromBoolean(x.wet)
        )
    )

  implicit val itemInstance: DynamoFormat[Item] =
    DynamoFormat.ObjectFormat.build[Item](
      o => o.get[String]("name").map(Item),
      x =>
        DynamoObject(
          "name" -> DynamoValue.fromString(x.name)
        )
    )

  implicit val lemmingInstance: DynamoFormat[Lemming] =
    DynamoFormat.ObjectFormat.build[Lemming](
      o => (o.get[String]("name"), o.get[String]("stuff")).mapN(Lemming),
      x =>
        DynamoObject(
          "name" -> DynamoValue.fromString(x.name),
          "stuff" -> DynamoValue.fromString(x.stuff)
        )
    )

  implicit val rabbitInstance: DynamoFormat[Rabbit] =
    DynamoFormat.ObjectFormat.build[Rabbit](
      o => o.get[String]("name").map(Rabbit),
      x =>
        DynamoObject(
          "name" -> DynamoValue.fromString(x.name)
        )
    )

  implicit val stationInstance: DynamoFormat[Station] =
    DynamoFormat.ObjectFormat.build[Station](
      o => (o.get[String]("mode"), o.get[String]("name"), o.get[Int]("zone")).mapN(Station),
      x =>
        DynamoObject(
          "mode" -> DynamoValue.fromString(x.mode),
          "name" -> DynamoValue.fromString(x.name),
          "zone" -> DynamoValue.fromNumber(x.zone)
        )
    )

  implicit val transportInstance: DynamoFormat[Transport] =
    DynamoFormat.ObjectFormat.build[Transport](
      o => (o.get[String]("mode"), o.get[String]("line"), o.get[String]("colour")).mapN(Transport),
      x =>
        DynamoObject(
          "mode" -> DynamoValue.fromString(x.mode),
          "line" -> DynamoValue.fromString(x.line),
          "colour" -> DynamoValue.fromString(x.colour)
        )
    )

  implicit val workerInstance: DynamoFormat[Worker] =
    DynamoFormat.ObjectFormat.build[Worker](
      o => (o.get[String]("firstName"), o.get[String]("surname"), o.get[Option[Int]]("age")).mapN(Worker),
      x =>
        DynamoObject(
          "firstName" -> DynamoValue.fromString(x.firstName),
          "surname" -> DynamoValue.fromString(x.surname)
        ) <> x.age.fold(DynamoObject.empty)(a => DynamoObject.singleton("age", DynamoValue.fromNumber(a)))
    )

}
