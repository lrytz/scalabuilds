package controllers

import akka.util.duration._
import models.Setting

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

//  val localGitRepoDir = "git-repo"

  val revListerUrl = "http://localhost:9001/"

//  val artifactsDir = "artifacts"

  lazy val jenkinsUsername = Setting.setting("jenkinsUsername")
  lazy val jenkinsPassword = Setting.setting("jenkinsPassword")
  
  val oldestImportedCommit = "0cffdf38d9e2d88e66d8649d317f8815716b2748"
}
