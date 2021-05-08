package org.scanamo.generic

import java.util.regex.Pattern

trait FieldNamingMode {
  def transformName(fieldName: String): String
}

trait NoOpFieldNamingMode extends FieldNamingMode {

  final override def transformName(fieldName: String): String = fieldName
}

trait PascalCaseFieldNamingMode extends FieldNamingMode {

  final override def transformName(fieldName: String): String = fieldName.capitalize
}

trait SnakeCaseFieldNamingMode extends FieldNamingMode {
  private val pattern: Pattern = Pattern.compile("([a-z\\d])([A-Z])")
  final override def transformName(fieldName: String): String =
    fieldName
      .replaceAll(pattern.pattern(), "$1_$2")
      .toLowerCase()
}

trait KebabCaseFieldNamingMode extends FieldNamingMode {
  private val pattern: Pattern = Pattern.compile("([a-z\\d])([A-Z])")
  final override def transformName(fieldName: String): String =
    fieldName
      .replaceAll(pattern.pattern(), "$1-$2")
      .toLowerCase()
}


