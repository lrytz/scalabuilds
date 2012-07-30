package controllers
package jenkins

import cc.spray.json._
import RichJsValue._
//import play.api.libs.json._
import dispatch._
import Application.silentHttp

import play.api.Logger

case class JenkinsBuildInfo(sha: String, buildId: Int, buildUUID: String, finished: Boolean, artifacts: List[String], buildSuccess: Option[Boolean])

private[jenkins] case class JenkinsParameter(name: String, value: JsValue)
private[jenkins] case class JenkinsBuild(number: Int, url: String)
private[jenkins] case class JenkinsArtifact(displayPath: String, fileName: String, relativePath: String)

private[jenkins] object JenkinsJsonProtocol extends DefaultJsonProtocol {
  implicit val jenkinsParameterFormat = jsonFormat2(JenkinsParameter)
  implicit val jenkinsBuildFormat = jsonFormat2(JenkinsBuild)
  implicit val jenkinsArtifactFormat = jsonFormat3(JenkinsArtifact)
}

object JenkinsTools {
  import JenkinsJsonProtocol._
  import Config._

  def existingBuilds(): List[Int] = {
    val req = url(jenkinsUrl + "job/"+jenkinsJob+"/api/json")
    val res = silentHttp(req >:+ { (headers, req) =>
      // todo: check stuff with header, fail if problem

      // handle request
      req >- { jsonString =>
      JsonParser(jsonString)
      }
    })
    val builds = (res \ "builds").arrayValues.map(_.convertTo[JenkinsBuild])
    val r = builds.map(_.number)
    Logger.info("found "+ r.length +" existing builds")
    r
  }
  
  def buildStream(buildIds: List[Int] = existingBuilds()): Stream[JenkinsBuildInfo] = buildIds match {
    case Nil => Stream.empty
    case x :: xs =>
      buildDetails(x) match {
        case Some(b) => Stream.cons(b, buildStream(xs))
        case None =>
          Logger.error("could not find build details for "+ x)
          buildStream(xs)
      }
  }
  
  def buildDetails(buildId: Int): Option[JenkinsBuildInfo] = {
    val req = url(jenkinsUrl + "job/"+ jenkinsJob +"/"+ buildId +"/api/json")
    val res = silentHttp(req >:+ { (headers, req) =>
      // todo: check stuff with header, fail if problem

      // handle request
      req >- { jsonString =>
        JsonParser(jsonString)
      }
    })
    Logger.info("got build details json for #"+ buildId)

    val actions = (res \ "actions").arrayValues
    val params = actions.filter(_.hasFieldNamed("parameters")) match{
      case List(action) =>
        (action \ "parameters").arrayValues.map(_.convertTo[JenkinsParameter])
      case Nil =>
        Logger.error("no build parameters found for jenkins build "+ buildId)
        Logger.error("actions: "+ actions)
        Nil
      case xs =>
        Logger.error("found multiple actions with field 'parameter' for jenkins build "+ buildId)
        Logger.error("actions: "+ actions)
        Nil
    }
    val shaOpt = params.find(_.name == "revision") match {
      case Some(p) => Some(p.value.convertTo[String])
      case None =>
        Logger.error("no 'revision' parameter found for jenkins build "+ buildId)
        Logger.error("params: "+ params)
        None
    }
    val buildUUIDOpt = params.find(_.name == "uuid") match {
      case Some(p) => Some(p.value.convertTo[String])
      case None =>
        Logger.error("no 'uuid' parameter found for jenkins build "+ buildId)
        Logger.error("params: "+ params)
        None
    }

    val finished = !(res \ "building").convertTo[Boolean]
    val artifacts = (res \ "artifacts").arrayValues.map(_.convertTo[JenkinsArtifact].relativePath)
    val buildSuccess = (res \ "result").convertTo[Option[String]] map {
      case "SUCCESS" => true
      case _ => false
    }
    
    for (sha <- shaOpt; buildUUID <- buildUUIDOpt)
      yield JenkinsBuildInfo(sha, buildId, buildUUID, finished, artifacts, buildSuccess)
  }

  def startBuild(sha: String, uuid: String, recipients: String, username: String = jenkinsUsername, password: String = jenkinsPassword): Boolean = {
    val baseReq = url(jenkinsUrl + "job/"+ jenkinsJob +"/buildWithParameters")
    val withParams = baseReq << Map("revision" -> sha, "uuid" -> uuid, "recipients" -> recipients)
    val req = withParams.POST.as_!(username, password)
    val res = silentHttp(req >:+ { (headers, req) =>
      // todo: check stuff with header, fail if problem

      // handle request
      req >- { result =>
        result
      }
    })
    Logger.info("started jenkins build for "+ sha)
    true
  }

  def searchJenkinsCommit(buildUUID: String): Option[JenkinsBuildInfo] = {
    import models.BuildUUID

    BuildUUID.buildNumber(buildUUID) match {
      case Some(uuidEntry) =>
        buildDetails(uuidEntry.jenkinsBuild)

      case None =>
        val allIds = existingBuilds()
        val idsInDB = BuildUUID.existingBuildNumbers()
        val newIds = allIds diff idsInDB
        Logger.info("Looking for UUID "+ buildUUID +", checking new builds: "+ newIds)
        buildStream(newIds).find(buildInfo => {
          // add everything we parse to the DB
          BuildUUID.add(buildInfo.buildUUID, buildInfo.buildId)
          buildInfo.buildUUID == buildUUID
        })
    }
  }
}
