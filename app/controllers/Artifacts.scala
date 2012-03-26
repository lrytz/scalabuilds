// no longer in use. we keep them on jenkins
/*
package controllers

import play.api.Logger

import scalax.io._
import scalax.file._

import models._
import jenkins.JenkinsTools

object Artifacts {
  lazy val artifactsPath = Path(Config.artifactsDir)

  def artifactsFor(sha: String): List[String] = {
    val path = artifactsPath / sha
    if (path.exists) (path ** "*").toList.filter(_.isFile).map(_.relativize(path)).map(_.path)
    else Nil
  }

  def delteArtifactsFor(sha: String) = {
    (artifactsPath / sha).deleteRecursively()
  }

  def downloadArtifacts(commit: Commit) {
    delteArtifactsFor(commit.sha)
    for (buildNb <- commit.jenkinsBuild) {
      
      val logUrl = Config.jenkinsUrl +"job/"+ Config.jenkinsJob +"/"+ buildNb +"/consoleText"
      val logOut = (artifactsPath / commit.sha / "log.txt").toAbsolute.path
      download(logUrl, logOut)
      
      for (buildDetails <- JenkinsTools.buildDetails(buildNb); artifact <- buildDetails.artifacts) {
        val url = Config.jenkinsUrl +"job/"+ Config.jenkinsJob +"/"+ buildNb +"/artifact/"+ artifact
        Logger.info("downloading "+ url)

        val filePath = Path(artifact.split("/"): _*)
        val outPath = (artifactsPath / commit.sha / filePath).toAbsolute.path
        
        download(url, outPath)
      }
    }
    Logger.info("Done downloading artifacts for "+ commit)
  }
  
  private def download(url: String, outFile: String) {
    val in: Input = Resource.fromURL(url)
    val out: Output = Resource.fromFile(outFile)

    Logger.info("downloading file "+ url +"\nto loacal file "+ outFile)
    in.copyDataTo(out)
  }
}
*/
