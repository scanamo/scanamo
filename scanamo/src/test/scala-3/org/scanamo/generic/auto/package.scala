package org.scanamo.generic
import org.scanamo.DynamoFormat
import org.scanamo.fixtures._
import org.scanamo._
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
  implicit val githubProjectInst:DynamoFormat[GithubProject] = deriveDynamoFormat[GithubProject]  
  implicit val keyInst:DynamoFormat[Key] = deriveDynamoFormat[Key]
  implicit val keyInstExported:Exported[DynamoFormat[Key]] = Exported(keyInst)
  implicit val BarInst: DynamoFormat[TableTest.Bar] = deriveDynamoFormat[TableTest.Bar]
  implicit val CharacterInst: DynamoFormat[TableTest.Character] = deriveDynamoFormat[TableTest.Character]
  implicit val ChoiceInst: DynamoFormat[TableTest.Choice] = deriveDynamoFormat[TableTest.Choice]
  implicit val CompoundInst: DynamoFormat[TableTest.Compound] = deriveDynamoFormat[TableTest.Compound]
  implicit val EventInst: DynamoFormat[TableTest.Event] = deriveDynamoFormat[TableTest.Event]
  implicit val FooInst: DynamoFormat[TableTest.Foo] = deriveDynamoFormat[TableTest.Foo]
  implicit val FruitInst: DynamoFormat[TableTest.Fruit] = deriveDynamoFormat[TableTest.Fruit]
  implicit val InnerInst: DynamoFormat[TableTest.Inner] = deriveDynamoFormat[TableTest.Inner]
  implicit val LetterInst: DynamoFormat[TableTest.Letter] = deriveDynamoFormat[TableTest.Letter]
  implicit val MiddleInst: DynamoFormat[TableTest.Middle] = deriveDynamoFormat[TableTest.Middle]
  implicit val OuterInst: DynamoFormat[TableTest.Outer] = deriveDynamoFormat[TableTest.Outer]
  implicit val ThingInst: DynamoFormat[TableTest.Thing] = deriveDynamoFormat[TableTest.Thing]
  implicit val Thing2Inst: DynamoFormat[TableTest.Thing2] = deriveDynamoFormat[TableTest.Thing2]
  implicit val TurnipInst: DynamoFormat[TableTest.Turnip] = deriveDynamoFormat[TableTest.Turnip]
end auto