package controllers

import akka.util.duration._

object Config {
  val commitsPerPage = 50
  
  val enablePollings = true
  
  val updatePollFrequency = 1 minute
  val refreshRunningBuildsFrequency = 30 seconds
  
  val githubUser = "scala"
  val githubRepo = "scala"
  val githubBranch = "master"
    
  val jenkinsUrl = "https://scala-webapps.epfl.ch/jenkins/"
  val jenkinsJob = "scala-checkin"
    
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
}
