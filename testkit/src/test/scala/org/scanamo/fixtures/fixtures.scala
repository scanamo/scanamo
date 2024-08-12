package org.scanamo.fixtures

case class Animal(species: String, number: Int)
case class Bear(name: String, favouriteFood: String, alias: Option[String] = None)
case class City(name: String, country: String)
case class Doctor(actor: String, regeneration: Int)
case class Engine(name: String, number: Int)
case class Factory(id: Int, name: String)
case class Farm(animals: List[String], hectares: Int = 100)
case class Farmer(name: String, age: Long, farm: Farm)
case class Forecast(location: String, weather: String, equipment: Option[String] = None)
case class Gremlin(number: Int, wet: Boolean, friendly: Boolean = true)
case class Item(name: String)
case class Lemming(name: String, stuff: String)
case class Rabbit(name: String)
case class Station(line: String, name: String, zone: Int)
case class Transport(mode: String, line: String, colour: String)
case class Worker(firstName: String, surname: String, age: Option[Int])
case class GithubProject(organisation: String, repository: String, language: String, license: String)
case class Key(mode: String, line: String, colour: String)
object Bank {
  case class Account(sortCode: Int, accountNumber: Int, name: String, balance: Int, frozen: Boolean = false, transactionIds: Set[String] = Set.empty)
  case class Transfer(guid: String, fromAccount: (Int, Int), toAccount: (Int, Int), amount: Int)
}
