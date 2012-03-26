package models

import play.api.Logger

import anorm._
import anorm.SqlParser._

import play.api.db._
import play.api.Play.current

case class User(id: Int, email: String)

object User {
  private val userParser = {
    get[Int]("id") ~
    get[String]("email") map {
      case id ~ email =>
        User(id, email)
    }
  }

  def userExists(email: String) = {
    DB.withConnection { implicit c =>
      SQL("select * from login where email={email}").on(
          'email -> email
          ).as(userParser *)
    }.nonEmpty
  }
}
