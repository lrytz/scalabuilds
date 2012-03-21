package controllers

import play.api._
import play.api.mvc._

import play.api.db._
import play.api.Play.current

import play.api.libs.concurrent.Akka
import akka.actor.Props
import akka.util.duration._
import play.api.libs.concurrent.Promise
import scala.concurrent.stm._

import anorm._
import java.util.UUID

import collection.{mutable => imm}

import github._
import jenkins._
import models._

object Application extends Controller {

  val pollGithubActor = Akka.system.actorOf(Props[PollGithubActor])
  if (Config.enablePollings)
    Akka.system.scheduler.schedule(30 seconds, Config.updatePollFrequency, pollGithubActor, "update")
  
  val refreshBuildsActor = Akka.system.actorOf(Props[RefreshBuildsActor])
  if(Config.enablePollings)
    Akka.system.scheduler.schedule(10 seconds, Config.refreshRunningBuildsFrequency, refreshBuildsActor, "refresh")

  /* Ref to prevent concurrent updates of the revision list */
  val appState = Ref[UpdateState](Idle)

  /* Ref to prevent concurrent write tasks on a commit */
  val commitTasks = Ref[imm.Map[String, Promise[Unit]]](imm.Map())
  private[controllers] def submitCommitTask(sha: String, action: => Unit): Boolean = {
    atomic { implicit txn =>
      commitTasks().get(sha) match {
        case None =>
          commitTasks.transform(_ + (sha -> Akka.future {
            try {
              action
            } catch {
              case e =>
                Logger.error(e.getClass.toString +": "+ e.getMessage +"\n"+ e.getStackTraceString)
                throw e
            } finally {
              commitTasks.single.transform(_ - sha)
            }
          }))
          true
        case Some(_) =>
          false
      }
    }
  }
  
  //val pollUpdateActor = Akka.system.actorOf()


  def index(page: Int) = Action {
    if (page < 1) NotFound(views.html.error("Page index out of bounds: "+ page))
    else {
      val commits = Commit.commits(page = page)
      val refreshing = commitTasks.single().keySet.toSet
      Ok(views.html.index(commits, refreshing, appState.single(), page))
    }
  }

  def revPage(sha: String) = Action {
    Commit.commit(sha) match {
      case Some(c) => Ok(views.html.revision(c, commitTasks.single().contains(sha), Config.jenkinsJob))
      case None => NotFound(views.html.error("Unknown commit hash: "+ sha))
    }
  }

  def download(sha: String, file: String, read: Boolean) = Action {
    import play.api.libs.iteratee.Enumerator
    import scalax.file._

    val subPath = Path(file.split("/"): _*)
    val filePath = (Artifacts.artifactsPath / sha / subPath).toAbsolute
    Logger.info("serving file "+ filePath)

    if (filePath.exists) {
      Ok.sendFile(new java.io.File(filePath.path), inline = read)
    } else {
      NotFound(views.html.error("File not found: "+ sha +"/artifacts/"+ file))
    }
  }
  
  def startBuild(sha: String) = {
    val ok = submitCommitTask(sha, doStartBuild(sha))
    Action {
      if (ok) Redirect(routes.Application.revPage(sha))
      else Conflict(views.html.error("Could not start build, action in progress for "+ sha))
    }
  }

  def cancelBuild(sha: String) = {
    val ok = submitCommitTask(sha, doCancel(sha))
    Action {
      if (ok) Redirect(routes.Application.revPage(sha))
      else Conflict(views.html.error("Could not cancel build, action in progress for "+ sha))
    }
  }

  def refresh(sha: String) = {
    val ok = submitCommitTask(sha, doRefresh(sha))
    Action {
      if (ok) Redirect(routes.Application.revPage(sha))
      else Conflict("Could not refresh build, action in progress for "+ sha)
    }
  }

  
  def updateRepo() = {
    Action {
      if (submitUpdateRepoTask()) Redirect(routes.Application.index())
      else Conflict(views.html.error("Repository update already in progress."))
    }
  }

  def refreshAll() = {
    doRefreshAll()
    Action {
      Redirect(routes.Application.index())
    }
  }
  

  private[controllers] def submitUpdateRepoTask() = {
    atomic { implicit txn =>
      appState() match {
        case Idle =>
          appState() = Updating(Akka.future {
            val res = try { 
              doUpdateRepo()
            } catch {
              case e => 
                Logger.error(e.getClass.toString +": "+ e.getMessage +"\n"+ e.getStackTraceString)
                throw e
            } finally {
              appState.single() = Idle
            }
            res
          })
          true
        case Updating(_) =>
          false
      }
    }
  }


  /* The action logic */
  
  private def doStartBuild(sha: String) {
    Commit.commit(sha) match {
      case Some(commit) =>
        commit.state match {
          case Missing =>
            Logger.info("Starting new build for "+ commit)
            val uuid = UUID.randomUUID.toString
            Commit.updateState(sha, Searching)
            Commit.updateJenkinsBuildUUID(sha, Some(uuid))
            JenkinsTools.startBuild(sha, uuid)

          case Done =>
            Logger.info("Re-building "+ commit)
            val uuid = UUID.randomUUID.toString
            Commit.updateJenkinsBuild(sha, None)
            Commit.updateState(sha, Searching)
            Commit.updateJenkinsBuildUUID(sha, Some(uuid))
            JenkinsTools.startBuild(sha, uuid)
            
          case Searching | Running | Downloading =>
            Logger.error("Cannot start running build: "+ commit)
        }
        
      case None =>
        Logger.error("Commit to start not found: "+ sha)        
    }
  }
  
  
  private def doCancel(sha: String) {
    Commit.commit(sha) match {
      case Some(commit) =>
        commit.state match {
          case Missing =>
            Logger.error("Commit is not running, cannot cancel: "+ commit)
        
          case Searching | Running | Downloading | Done =>
            Logger.info("Canceling "+ commit)
            Commit.updateJenkinsBuild(sha, None)
            Commit.updateJenkinsBuildUUID(sha, None)
            Commit.updateState(sha, Missing)
        }
        
      case None =>
        Logger.error("Commit to cancel not found: "+ sha)
    }
  }

  
  private[controllers] def doRefreshAll() {
    for (commit <- Commit.runningCommits) {
      Logger("refreshing build "+ commit)
      submitCommitTask(commit.sha, doRefresh(commit.sha))
    }
  }
  
  private def doRefresh(sha: String) {
    import JenkinsTools._
    
    Commit.commit(sha) match {
      case Some(commit) =>
        commit.state match {
          case Missing | Done =>
            Logger.info("Nothing to refresh for commit "+ commit)
          case Searching =>
            searchJenkinsCommit(commit.jenkinsBuildUUID.get) match {
              case Some(buildInfo) =>
                Logger.info("Found jenkins build "+ buildInfo +" for "+ commit)
                Commit.updateJenkinsBuild(sha, Some(buildInfo.buildId))
                Commit.updateState(sha, Running)
                doRefresh(sha)

              case None =>
                Logger.info("No jenkins build found for "+ commit)
            }

          case Running =>
            JenkinsTools.buildDetails(commit.jenkinsBuild.get) match {
              case Some(jenkinsInfo) if jenkinsInfo.finished =>
                Logger.info("Jenkins build finished for "+ commit)
                Commit.updateBuildSuccess(sha, jenkinsInfo.buildSuccess)
                Commit.updateState(sha, Downloading)
                try {
                  Artifacts.downloadArtifacts(commit)
                } finally {
                  Commit.updateState(commit.sha, Done)
                }
              case Some(_) =>
                Logger.info("Jenkins build still running for "+ commit)
              case None =>
                Logger.error("could not find build when checking finished status of "+ commit.jenkinsBuild.get)
            }

          case Downloading =>
            Logger.info("Nothing to refresh while downloading "+ commit)
        }

      case None =>
        Logger.error("Commit to refresh not found: "+ sha)
    }
    
  }


  private def doUpdateRepo(): List[Commit] = {
    val commits = Commit.commits(page = 1, num = 1)
    if (commits.isEmpty) {
      Logger.error("No existing commits found when trying doUpdateRepo")
      Nil
    } else {
      val latest = commits.head
      val newCommits = GithubTools.revisionStream().takeWhile(_.sha != latest.sha).toList
      Logger.info("Fetched new commits: "+ newCommits)
      Commit.addCommits(newCommits)
      for (commit <- newCommits)
        submitCommitTask(commit.sha, doStartBuild(commit.sha))
      newCommits
    }
  }

  
  
  
  
  /*********************
   * STUFF 
   *********************/

  
  def initRepo() = {
    val commits = GithubTools.fetchRevisions(100)
    Commit.addCommits(commits)
    Action {
      Redirect(routes.Application.index())
    }
  }
}
