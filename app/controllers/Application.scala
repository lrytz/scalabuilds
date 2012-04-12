package controllers

import play.api._
import play.api.mvc._

import play.api.db._

import play.api.libs.openid._
import play.api.libs.concurrent.{Redeemed, Thrown}

import play.api.libs.concurrent.Akka
import play.api.Play.current

import akka.actor.Props
import akka.util.duration._
import play.api.libs.concurrent.Promise

import anorm._
import java.util.UUID

import collection.{mutable => imm}

import github._
import jenkins._
import models._

import UpdateRepoActor.submitUpdateTask
import BuildTasksDispatcher.submitBuildTask

object Application extends Controller {

  /**
   * The actors
   */
  val updateRepoActor = Akka.system.actorOf(Props[UpdateRepoActor])
  if (Config.enablePollings)
    Akka.system.scheduler.schedule(30 seconds, Config.updatePollFrequency, updateRepoActor, "update")
  
  val refreshRunningBuildsActor = Akka.system.actorOf(Props[RefreshRunningBuildsActor])
  if(Config.enablePollings)
    Akka.system.scheduler.schedule(30 seconds, Config.refreshRunningBuildsFrequency, refreshRunningBuildsActor, "refresh")

  val buildTasksDispatcherActor = Akka.system.actorOf(Props[BuildTasksDispatcher])

  
  /**
   * Login related
   */
  
  def login(message: String) = Action {
    Ok(views.html.login(message))
  }
  
  def loginPost() = Action { implicit request =>
    val discoveryUrl = "https://www.google.com/accounts/o8/id"
    val requireEmail = List("email" -> "http://axschema.org/contact/email")
    val redirUrl = OpenID.redirectURL(discoveryUrl, routes.Application.openIDCallback.absoluteURL(), axRequired = requireEmail)
    AsyncResult(redirUrl.extend(_.value match {
      case Redeemed(url) => Redirect(url)
      case Thrown(t) => Redirect(routes.Application.login(t.toString))
    }))
  }
  
  def openIDCallback() = Action { implicit request =>
    AsyncResult(
      OpenID.verifiedId.extend( _.value match {
        case Redeemed(info) =>
          Redirect(routes.Application.index()).withSession("id" -> info.id, "email" -> info.attributes("email"))
//          Ok(info.id + "\n" + info.attributes)
        case Thrown(t) => {
          Redirect(routes.Application.login(t.toString))
        }
      })
    )
  }
  
  def logout = Action {
    Redirect(routes.Application.login("Logout successful")).withNewSession
  }

  def userAllowed(email: String) = models.User.userExists(email)
  def isAuth(implicit request: Request[AnyContent]) = request.session.get("email").map(mail => userAllowed(mail)).getOrElse(false)
  private def username(request: RequestHeader) = request.session.get("email")
  private def onUnauthorized(request: RequestHeader) = Results.Redirect(routes.Application.login("Login required"))
  def AuthAction(f: => Request[AnyContent] => Result) = Security.Authenticated(username, onUnauthorized) { user =>
    Action { request => 
      if (userAllowed(user)) f(request)
      else Forbidden(views.html.error("User "+ user +" not allowed. Ask the admin to add your gmail address."))
    }
  }
  
  /**
   * The pages
   */

  def index(page: Int) = Action { implicit request =>
    if (page < 1) NotFound(views.html.error("Page index out of bounds: "+ page))
    else {
      val commits = Commit.commits(page = page)
      val refreshing = BuildTasksDispatcher.busyList
      Ok(views.html.index(commits, refreshing, UpdateRepoActor.appState, page, isAuth))
    }
  }

  def revPage(sha: String) = Action { implicit request =>
    val isRefreshing = BuildTasksDispatcher.busyList(sha)
    Commit.commit(sha) match {
      case Some(c) => Ok(views.html.revision(c, isRefreshing, Config.jenkinsJob, isAuth))
      case None => NotFound(views.html.error("Unknown commit hash: "+ sha))
    }
  }

  def downloadRedirect(sha: String, file: String) = Action {
    Commit.commit(sha) match {
      case Some(c) =>
        val fileUrl = Config.jenkinsUrl +"job/"+ Config.jenkinsJob +"/"+ c.jenkinsBuild.get +"/artifact/"+ file
        Redirect(fileUrl)

      case None =>
        NotFound(views.html.error("Unknown commit hash: "+ sha))
    }
  }

/*
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
*/

  def startBuild(sha: String) = AuthAction { _ =>
    val ok = submitBuildTask(sha, doStartBuild(sha))
    if (ok) Redirect(routes.Application.revPage(sha))
    else Conflict(views.html.error("Could not start build, action in progress for "+ sha))
  }

  def cancelBuild(sha: String) = AuthAction { _ =>
    val ok = submitBuildTask(sha, doCancel(sha))
    if (ok) Redirect(routes.Application.revPage(sha))
    else Conflict(views.html.error("Could not cancel build, action in progress for "+ sha))
  }

  def refresh(sha: String) = AuthAction { _ =>
    val ok = submitBuildTask(sha, doRefresh(sha))
    if (ok) Redirect(routes.Application.revPage(sha))
    else Conflict("Could not refresh build, action in progress for "+ sha)
  }

  
  def updateRepo() = AuthAction { _ =>
    if (submitUpdateTask()) Redirect(routes.Application.index())
    else Conflict(views.html.error("Repository update already in progress."))
  }

  def refreshAll() = AuthAction { _ =>
    doRefreshAll()
    Redirect(routes.Application.index())
  }


  /**
   * The application logic
   */
  
  private def doStartBuild(sha: String) {
    Commit.commit(sha) match {
      case Some(commit) =>
        commit.state match {
          case Missing | Done =>
            if (commit.state == Missing) {
              Logger.info("Starting new build for "+ commit)
            } else {
              doCancel(sha)
              Logger.info("Re-building "+ commit)
            }
            val uuid = UUID.randomUUID.toString
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
            Commit.updateBuildSuccess(sha, None)
            Commit.updateState(sha, Missing)
            for (art <- Artifact.artifactsFor(sha))
              Artifact.deleteArtifact(art.id)
        }
        
      case None =>
        Logger.error("Commit to cancel not found: "+ sha)
    }
  }

  
  private[controllers] def doRefreshAll() {
    for (commit <- Commit.runningCommits) {
      Logger("refreshing build "+ commit)
      submitBuildTask(commit.sha, doRefresh(commit.sha))
    }
  }
  
  /**
   * The state machine of a commit being built
   */
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
//                  Artifacts.downloadArtifacts(commit)
                  Artifact.storeArtifacts(commit)
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

  
  private def newCommitsSince(sha: String) = {
    import dispatch._
    val req = url(Config.revListerUrl + sha)
    Http(req >- { res =>
      res match {
        case "" => Nil
        case s  => s.split(",").toList
      }
    })
  }

  private[controllers] def doUpdateRepo() {
    val commits = Commit.commits(page = 1, num = 1)
    if (commits.isEmpty) {
      Logger.error("No existing commits found when trying doUpdateRepo")
      Nil
    } else {
      val latest = commits.head
      val newShas = newCommitsSince(latest.sha)
      if (newShas.isEmpty) {
        Logger.info("No new commits.")
      } else {
        val newCommits = newShas.map(GithubTools.revisionInfo(_))
        //val newCommits = GithubTools.revisionStream().takeWhile(_.sha != latest.sha).toList
        Logger.info("Fetched new commits: "+ newCommits)
        Commit.addCommits(newCommits)
        for (commit <- newCommits)
          submitBuildTask(commit.sha, doStartBuild(commit.sha))
      }
    }
  }

  
  
  
 
  /**
   * Random stuff
   */
  
  def sql() = Action {
    import dispatch._
    import play.api.db._
    import play.api.Play.current

    val req = url("http://lamp.epfl.ch/~rytz/script.sql")

    Http(req >~ { source =>
      for (line <- source.getLines())
      DB.withConnection { implicit c =>
        if (line.nonEmpty) {
          Logger.info(line)
          SQL(line).executeUpdate()
        }
      }
    })

    Redirect(routes.Application.index())
  }

  def initRepo() = AuthAction { _ =>
    val shas = newCommitsSince(Config.oldestImportedCommit).filter(r => (Commit.commit(r).isEmpty))
    val commits = shas.map(sha => GithubTools.revisionInfo(sha))
    Commit.addCommits(commits)
    Redirect(routes.Application.index())
  }
}
