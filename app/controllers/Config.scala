package controllers

import akka.util.duration._
import models.Setting

object Config {
  val commitsPerPage = 50
  
  lazy val enablePollings = Setting.setting("enablePollings") == "true"
  
  val updatePollFrequency = 3 minutes
  val refreshRunningBuildsFrequency = 15 minutes
  
  val githubUser = "scala"
  val githubRepo = "scala"
  val githubBranch = "master"

  val jenkinsUrl = "https://scala-webapps.epfl.ch/jenkins/"
  val jenkinsJob = "scala-checkin"

//  val localGitRepoDir = "git-repo"

  val revListerUrl = "http://scala-webapps.epfl.ch/rev-lister/"

//  val artifactsDir = "/Users/luc/Downloads/backup/artifacts"

  lazy val jenkinsUsername = Setting.setting("jenkinsUsername")
  lazy val jenkinsPassword = Setting.setting("jenkinsPassword")
  
  val newCommitBuildRecipients = "scala-reports@epfl.ch"
  val manualBuildRecipients    = "lukas.rytz@epfl.ch"
  
  val oldestImportedCommit = "0cffdf38d9e2d88e66d8649d317f8815716b2748"
}
