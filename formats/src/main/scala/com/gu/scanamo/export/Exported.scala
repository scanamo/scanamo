package com.gu.scanamo.export

// Thanks Travis Brown and team!
// https://github.com/circe/circe/blob/master/LICENSE

case class Exported[+T](instance: T) extends AnyVal
