package controllers

import scalax.file._
import process._

object GitRepo {
  val gitRepoDir = "git-repo"

  lazy val repoPath = Path(gitRepoDir)
  lazy val someRepoFile = Some(new java.io.File(repoPath.toAbsolute.path))
  lazy val someRepoParentFile = Some(new java.io.File(repoPath.toAbsolute.parent.get.path))

  def cloneGitRepo() {
    repoPath.deleteRecursively(true)
    val repoUrl = "git://github.com/scala/scala.git"
    Process("git clone "+ repoUrl +" "+ gitRepoDir, someRepoParentFile).!!
  }

  def pullLatest() {
    Process("git pull origin master", someRepoFile).!!
  }
  
  def runRevList(arg: String): Either[Throwable, List[String]] = {
    try {
      if (!repoPath.exists) {
        cloneGitRepo()
      }
      pullLatest()
      println("git rev-list "+ arg)
      Right(Process("git rev-list "+ arg, someRepoFile).lines.toList)
    } catch {
      case e: Exception =>
        Left(e)
    }
  }
}
