package org.scanamo

trait LocalDBBase[Sync, Async, ScalarAttributeType, CreateResult, DeleteResult] {

  def clientSync(): Sync

  def clientAsync(): Async

  def createTable(client: Sync)(tableName: String)(attributes: (Symbol, ScalarAttributeType)*): CreateResult

  def createTableWithIndex(
    client: Sync,
    tableName: String,
    secondaryIndexName: String,
    primaryIndexAttributes: List[(Symbol, ScalarAttributeType)],
    secondaryIndexAttributes: List[(Symbol, ScalarAttributeType)]
  ): CreateResult

  def deleteTable(client: Sync)(tableName: String): DeleteResult

  def withTable[T](client: Sync)(tableName: String)(attributeDefinitions: (Symbol, ScalarAttributeType)*)(
    thunk: => T
  ): T = {
    createTable(client)(tableName)(attributeDefinitions: _*)
    val res = try {
      thunk
    } finally {
      deleteTable(client)(tableName)
      ()
    }
    res
  }

  def withRandomTable[T](client: Sync)(attributeDefinitions: (Symbol, ScalarAttributeType)*)(
    thunk: String => T
  ): T = {
    var created: Boolean = false
    var tableName: String = null
    while (!created) {
      try {
        tableName = java.util.UUID.randomUUID.toString
        createTable(client)(tableName)(attributeDefinitions: _*)
        created = true
      } catch {
        case e: Throwable =>
      }
    }

    val res = try {
      thunk(tableName)
    } finally {
      deleteTable(client)(tableName)
      ()
    }
    res
  }

  def usingTable[T](client: Sync)(tableName: String)(attributeDefinitions: (Symbol, ScalarAttributeType)*)(
    thunk: => T
  ): Unit = {
    withTable(client)(tableName)(attributeDefinitions: _*)(thunk)
    ()
  }

  def usingRandomTable[T](client: Sync)(attributeDefinitions: (Symbol, ScalarAttributeType)*)(
    thunk: String => T
  ): Unit = {
    withRandomTable(client)(attributeDefinitions: _*)(thunk)
    ()
  }

  def withTableWithSecondaryIndex[T](client: Sync)(tableName: String, secondaryIndexName: String)(
    primaryIndexAttributes: (Symbol, ScalarAttributeType)*
  )(secondaryIndexAttributes: (Symbol, ScalarAttributeType)*)(
    thunk: => T
  ): T = {
    createTableWithIndex(
      client,
      tableName,
      secondaryIndexName,
      primaryIndexAttributes.toList,
      secondaryIndexAttributes.toList
    )
    val res = try {
      thunk
    } finally {
      deleteTable(client)(tableName)
      ()
    }
    res
  }

  def withRandomTableWithSecondaryIndex[T](
    client: Sync
  )(primaryIndexAttributes: (Symbol, ScalarAttributeType)*)(secondaryIndexAttributes: (Symbol, ScalarAttributeType)*)(
    thunk: (String, String) => T
  ): T = {
    var tableName: String = null
    var indexName: String = null
    var created: Boolean = false
    while (!created) {
      try {
        tableName = java.util.UUID.randomUUID.toString
        indexName = java.util.UUID.randomUUID.toString
        createTableWithIndex(
          client,
          tableName,
          indexName,
          primaryIndexAttributes.toList,
          secondaryIndexAttributes.toList
        )
        created = true
      } catch {
        case t: Throwable =>
      }
    }

    val res = try {
      thunk(tableName, indexName)
    } finally {
      deleteTable(client)(tableName)
      ()
    }
    res
  }

}

