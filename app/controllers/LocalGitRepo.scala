
// no longer in use - now a separate webapp (having a local repo is not possible on heroku)

/*
package controllers

import scalax.file._
import sys.process._

import Config._

object LocalGitRepo {
  lazy val repoPath = Path(localGitRepoDir)
  lazy val someRepoFile = Some(new java.io.File(repoPath.toAbsolute.path))
  lazy val someRepoParentFile = Some(new java.io.File(repoPath.toAbsolute.parent.get.path))

  def cloneGitRepo() {
    repoPath.deleteRecursively(true)
    val repoUrl = "git@github.com:"+githubUser+"/"+githubRepo+".git"
    Process("git clone "+ repoUrl +" "+ localGitRepoDir, someRepoParentFile).!!
  }
  
  def pullLatest() {
    Process("git pull origin master", someRepoFile).!!
  }
  
  def newCommitsSince(sha: String): List[String] = {
    pullLatest()
    Process("git rev-list HEAD ^"+ sha, someRepoFile).lines.toList
  }
  
  def allCommits(): List[String] = {
    pullLatest()
    Process("git rev-list HEAD", someRepoFile).lines.toList
  }
}
*/