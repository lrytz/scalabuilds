package models

import play.api.Logger

import anorm._
import anorm.SqlParser._

import play.api.db._
import play.api.Play.current

case class BuildUUID(uuid: String, jenkinsBuild: Int)
object BuildUUID {
  private val buildUUIDParser = {
    get[String]("uuid") ~
    get[Int]("jenkinsBuild") map {
      case uuid ~ jenkinsBuild=>
        BuildUUID(uuid, jenkinsBuild)
    }
  }

  def buildNumber(uuid: String): Option[BuildUUID] = {
    DB.withConnection { implicit c =>
      SQL("select * from buildUUID where uuid={uuid}").on(
          'uuid -> uuid
          ).as(buildUUIDParser *)
    }.headOption
  }
  
  def existingBuildNumbers(): List[Int] = {
    DB.withConnection { implicit c =>
      SQL("select * from buildUUID").as(buildUUIDParser *)
    }.map(_.jenkinsBuild)
  }

  def add(uuid: String, jenkinsBuild: Int) {
    if (buildNumber(uuid).isDefined) {
      Logger.error("Build number already exists for "+ uuid)
    } else {
      DB.withConnection { implicit conn =>
      SQL("insert into buildUUID values ({uuid}, {jenkinsBuild})").on(
          'uuid -> uuid,
          'jenkinsBuild -> jenkinsBuild
          ).executeUpdate()
      }
    }
  }
}
