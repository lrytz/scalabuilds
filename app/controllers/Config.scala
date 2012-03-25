package controllers

import akka.util.duration._

object Config {
  val commitsPerPage = 50
  
  val enablePollings = false
  
  val updatePollFrequency = 1 minute
  val refreshRunningBuildsFrequency = 5 minutes
  
  val githubUser = "scala"
  val githubRepo = "scala"
  val githubBranch = "master"
    
  val jenkinsUrl = "https://scala-webapps.epfl.ch/jenkins/"
  val jenkinsJob = "scala-checkin"

  val localGitRepoDir = "git-repo"

  val artifactsDir = "artifacts"

  val jenkinsCredentialsFile = "jenkinsCredentials.json"
  private def read(field: String) = {
    import cc.spray.json._
    import DefaultJsonProtocol._
    import RichJsValue._
    val js = JsonParser(io.Source.fromFile(jenkinsCredentialsFile).mkString)
    (js \ field).convertTo[String]
  }
  lazy val jenkinsUsername = read("username")
  lazy val jenkinsPassword = read("password")

  val allowedUsers = Set(
    "rytz.epfl@gmail.com",
    "lukas.rytz@gmail.com",
    "adriaanm@gmail.com",
    "joshua.suereth@gmail.com",
    "paul.phillips@gmail.com",
    "hubert.plociniczak@gmail.com",
    "vlad.ureche@gmail.com",
    "odersky@gmail.com"
  )

  val oldestImportedCommit = "0cffdf38d9e2d88e66d8649d317f8815716b2748"
}
