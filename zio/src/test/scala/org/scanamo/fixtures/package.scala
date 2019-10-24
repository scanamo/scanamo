package org.scanamo

package object fixtures {
  
  implicit val animalInstance: DynamoFormat[Animal] =
    DynamoFormat.iso(
      (t: (String, Int)) => Animal(t._1, t._2),
      a => (a.species, a.number)
    )

  implicit val bearInstance: DynamoFormat[Bear] =
    DynamoFormat.iso(
      (t: (String, String, Option[String])) => Bear(t._1, t._2, t._3),
      b => (b.name, b.favouriteFood, b.alias)
    )
  
  implicit val cityInstance: DynamoFormat[City] =
    DynamoFormat.iso(
      (t: (String, String)) => City(t._1, t._2),
      c => (c.name, c.country)
    )
  
  implicit val doctorInstance: DynamoFormat[Doctor] = 
    DynamoFormat.iso(
      (t: (String, Int)) => Doctor(t._1, t._2),
      x => (x.actor, x.regeneration)
    )

  implicit val engineInstance: DynamoFormat[Engine] = 
    DynamoFormat.iso(
      (t: (String, Int)) => Engine(t._1, t._2),
      x => (x.name, x.number)
    )
  
  implicit val factoryInstance: DynamoFormat[Factory] = 
    DynamoFormat.iso(
      (t: (Int, String)) => Factory(t._1, t._2),
      x => (x.id, x.name)
    )

  implicit val farmInstance: DynamoFormat[Farm] = 
    DynamoFormat.iso[Farm, List[String]](Farm(_), _.asyncAnimals)
  
  implicit val farmerInstance: DynamoFormat[Farmer] = 
    DynamoFormat.iso(
      (t: (String, Long, Farm)) => Farmer(t._1, t._2, t._3),
      x => (x.name, x.age, x.farm)
    )
  
  implicit val forecastInstance: DynamoFormat[Forecast] = 
    DynamoFormat.iso(
      (t: (String, String, Option[String])) => Forecast(t._1, t._2, t._3),
      x => (x.location, x.weather, x.equipment)
    )
  
  implicit val gremlinInstance: DynamoFormat[Gremlin] = 
    DynamoFormat.iso(
      (t: (Int, Boolean)) => Gremlin(t._1, t._2),
      x => (x.number, x.wet)
    )
  
  implicit val itemInstance: DynamoFormat[Item] = 
    DynamoFormat.iso[Item, String](Item(_), _.name)
  
  implicit val lemmingInstance: DynamoFormat[Lemming] = 
    DynamoFormat.iso(
      (t: (String, String)) => Lemming(t._1, t._2),
      x => (x.name, x.stuff)
    )
  
  implicit val rabbitInstance: DynamoFormat[Rabbit] = 
    DynamoFormat.iso[Rabbit, String](Rabbit(_), _.name)
  
  implicit val stationInstance: DynamoFormat[Station] = 
    DynamoFormat.iso(
      (t: (String, String, Int)) => Station(t._1, t._2, t._3),
      x => (x.mode, x.name, x.zone)
    )
  
  implicit val transportInstance: DynamoFormat[Transport] = 
    DynamoFormat.iso(
      (t: (String, String, String)) => Transport(t._1, t._2, t._3),
      x => (x.mode, x.line, x.colour)
    )
  
  implicit val workerInstance: DynamoFormat[Worker] = 
    DynamoFormat.iso(
      (t: (String, String, Option[Int])) => Worker(t._1, t._2, t._3),
      x => (x.firstName, x.surname, x.age)
    )

}