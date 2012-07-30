package models

import java.util.Date
import java.text.SimpleDateFormat

import play.api.Logger

import anorm._
import anorm.SqlParser._

import play.api.db._
import play.api.Play.current

case class Commit(sha: String, commitDate: Date, githubUser: Option[String], authorName: String, state: State, jenkinsBuild: Option[Int], jenkinsBuildUUID: Option[String], buildSuccess: Option[Boolean]) {
  def dateString = new SimpleDateFormat("MMM d, yyyy").format(commitDate)
}

case object Commit {
  import controllers.Config._

  private val commitParser = {
    get[String]("sha") ~
    get[Long]("commitDate") ~
    get[Option[String]]("githubUser") ~
    get[String]("authorName") ~
    get[String]("state") ~
    get[Option[Int]]("jenkinsBuild") ~
    get[Option[String]]("jenkinsBuildUUID") ~
    get[Option[Boolean]]("buildSuccess") map {
      case sha ~ commitDate ~ githubUser ~ authorName ~ state ~ jenkinsBuild ~ jenkinsBuildUUID ~ buildSuccess =>
        Commit(sha, new Date(commitDate), githubUser, authorName, State(state), jenkinsBuild, jenkinsBuildUUID, buildSuccess)
    }
  }

  def commit(sha: String): Option[Commit] = {
    DB.withConnection { implicit c =>
      SQL("select * from commit where sha={sha}").on(
          'sha -> sha
          ).as(commitParser *)
    }.headOption
  }

  def commits(page: Int, num: Int = commitsPerPage): List[Commit] = {
    DB.withConnection { implicit c =>
      SQL("select * from commit order by commitDate desc limit {num} offset {offset}").on(
          'num -> num,
          'offset -> ((page-1)*num)
          ).as(commitParser *)
    }
  }
  
  def runningCommits(): List[Commit] = {
    DB.withConnection { implicit c =>
      SQL("select * from commit where state in ({new}, {search}, {run}, {download}) order by commitDate desc").on(
          'new -> New.toString,
          'search -> Searching.toString,
          'run -> Running.toString,
          'download -> Downloading.toString
          ).as(commitParser *)
    }
  }

  def addCommits(commits: List[Commit]) {
    for (c <- commits) {
      if (commit(c.sha).isDefined) {
        Logger.error("Commit already exists in the database: "+ c)
      } else {
        DB.withConnection { implicit conn =>
          // (sha, commitDate, githubUser, authorName, jenkinsBuild, state)
          SQL("insert into commit values ({sha}, {date}, {user}, {name}, {state}, {build}, {buildUUID}, {buildSuccess})").on(
              'sha -> c.sha,
              'date -> c.commitDate.getTime,
              'user -> c.githubUser,
              'name -> c.authorName,
              'state -> c.state.toString,
              'build -> c.jenkinsBuild,
              'buildUUID -> c.jenkinsBuildUUID,
              'buildSuccess -> c.buildSuccess
              ).executeUpdate()
        }
      }
    }
  }

  
  def updateField[T](sha: String, field: String, value: T) {
    DB.withConnection { implicit c =>
      SQL("update commit set "+field+"={value} where sha={sha}").on(
          'value -> value,
          'sha -> sha
          ).executeUpdate()
    }
  }

  def updateState(sha: String, state: State) {
    updateField(sha, "state", state.toString)
  }
  
  def updateJenkinsBuild(sha: String, jenkinsBuild: Option[Int]) {
    updateField(sha, "jenkinsBuild", jenkinsBuild)
  }

  def updateJenkinsBuildUUID(sha: String, jenkinsBuildUUID: Option[String]) {
    updateField(sha, "jenkinsBuildUUID", jenkinsBuildUUID)
  }

  def updateBuildSuccess(sha: String, buildSuccess: Option[Boolean]) {
    updateField(sha, "buildSuccess", buildSuccess)
  }

/*  def testCommits: List[Commit] = {
    val df = new SimpleDateFormat("dd.MM.yy")
    Commit("msabu2n", df.parse("01.02.12"), "rytz", None, Missing) ::
    Commit("klm18nj", df.parse("02.03.12"), "rytz", None, Missing) ::
    Commit("s0923nf", df.parse("01.01.12"), "odersky", None, Missing) ::
    Commit("209ndfh", df.parse("05.02.12"), "dragos", None, Missing) ::
    Nil
  } */
}

sealed trait State {
  override def toString() = State.mapToString(this)

  def describe(isRefreshing: Boolean) =
    if (isRefreshing && this != Downloading) "refreshing build state... (hit reload)"
    else this match {
      case Missing     => "no build available"
      case New         => "build not yet started"
      case Searching   => "build started, looking for jenkins build"
      case Running     => "build running"
      case Downloading => "downloading artifacts... (hit reload)"
      case Done        => "build available"
    }
}

object State {
  val mapToString: Map[State, String] = Map(
    Missing     -> "missing",
    New         -> "new",
    Searching   -> "searching",
    Running     -> "running",
    Downloading -> "downloading",
    Done        -> "done"
  )

  def apply(s: String): State = mapToString.map(_.swap).apply(s)
}

case object Missing     extends State // old commits: no build available, and we don't want to build now
case object New         extends State // new commits that we still want to build
case object Searching   extends State
case object Running     extends State
case object Downloading extends State
case object Done        extends State
