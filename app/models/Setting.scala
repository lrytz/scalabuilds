package models

import play.api.Logger

import anorm._
import anorm.SqlParser._

import play.api.db._
import play.api.Play.current

case class Setting(name: String, value: String)
object Setting {
  private val settingParser = {
    get[String]("name") ~
    get[String]("value") map {
      case name ~ value =>
        Setting(name, value)
    }
  }

  def setting(name: String) = {
    DB.withConnection { implicit c =>
      SQL("select * from setting where name={name}").on(
          'name -> name
          ).as(settingParser *)
    }.head.value
  }
}
