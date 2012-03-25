package models

import play.api.Logger

import anorm._
import anorm.SqlParser._

import play.api.db._
import play.api.Play.current

import controllers.Config

case class Artifact(id: Int, sha: String, filePath: String)

object Artifact {
  private val artifactParser = {
    get[Int]("id") ~
    get[String]("sha") ~
    get[String]("filePath") map {
      case id ~ sha ~ filePath =>
        Artifact(id, sha, filePath)
    }
  }

  def artifactsFor(sha: String): List[Artifact] = {
    DB.withConnection { implicit c =>
      SQL("select * from artifact where sha={sha}").on(
          'sha -> sha
          ).as(artifactParser *)
    }
  }

  def addArtifact(sha: String, filePath: String) {
    DB.withConnection { implicit conn =>
      SQL("insert into artifact (sha, filePath) values ({sha}, {filePath})").on(
          'sha -> sha,
          'filePath -> filePath
          ).executeUpdate()
    }
  }
  
  def storeArtifacts(commit: Commit) {
    for (buildNb <- commit.jenkinsBuild;
        buildDetails <- controllers.jenkins.JenkinsTools.buildDetails(buildNb);
        artifact <- buildDetails.artifacts) {
      Logger.info("adding artifact for "+ commit.sha +": "+ artifact)
      addArtifact(commit.sha, artifact)
    }
  }

  
  def deleteArtifact(id: Int) {
    DB.withConnection { implicit conn =>
      SQL("delete from artifact where id={id}").on(
          'id -> id
          ).executeUpdate()
    }
  }

/*
  def moveAllArtifacts() {
    for (commit <- Commit.commits(page = 1, num = 500)) {
      if (commit.state == Done) {
        for (artifact <- controllers.Artifacts.artifactsFor(commit.sha)) {
          if (artifact != "log.txt") {
            addArtifact(commit.sha, artifact)
          }
        }
      }
    }
  }
*/
}
