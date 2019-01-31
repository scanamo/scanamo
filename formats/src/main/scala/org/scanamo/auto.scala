package org.scanamo

/**
  * Fully automatic format derivation.
  *
  * Importing the contents of this package object provides [[org.scanamo.DynamoFormat]]
  * instances for case classes (if all members have instances)
  */
package object auto extends DerivedDynamoFormat
