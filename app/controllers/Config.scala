package controllers

import akka.util.duration._

object Config {
  val commitsPerPage = 50
  
  val enablePollings = true
  
  val updatePollFrequency = 1 minute
  val refreshRunningBuildsFrequency = 5 minutes
  
  val githubUser = "scala"
  val githubRepo = "scala"
  val githubBranch = "master"
    
  val jenkinsUrl = "https://scala-webapps.epfl.ch/jenkins/"
  val jenkinsJob = "scala-checkin"

  val localGitRepoDir = "git-repo"

  val artifactsDir = "artifacts"

  val jenkinsCredentialsFile = "/Users/luc/scala/scalabuilds/jenkinsCredentials.json"
  private def read(field: String) = {
    import cc.spray.json._
    import DefaultJsonProtocol._
    import RichJsValue._
    val js = JsonParser(io.Source.fromFile(jenkinsCredentialsFile).mkString)
    (js \ field).convertTo[String]
  }
  lazy val jenkinsUsername = read("username")
  lazy val jenkinsPassword = read("password")


  val oldestImportedCommit = "0cffdf38d9e2d88e66d8649d317f8815716b2748"
}
