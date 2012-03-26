package controllers

import play.api._
import play.api.mvc._

object Application extends Controller {
  def newCommits(sinceSha: String) = Action {
    Logger.info("request: "+ sinceSha)
    GitRepo.newCommitsSince(sinceSha) match {
      case Right(cs) => Ok(cs.mkString(","))
      case Left(e) => BadRequest("error occured: "+ e.toString)
    }
  }
}
