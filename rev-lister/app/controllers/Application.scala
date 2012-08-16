package controllers

import play.api._
import play.api.mvc._

object Application extends Controller {
  def revList(arg: String) = Action {
    Logger.info("request: "+ arg)
    if (validArg(arg)) {
      GitRepo.runRevList(arg) match {
        case Right(cs) => Ok(cs.mkString(","))
        case Left(e) => BadRequest("error occured: "+ e.toString)
      }
    } else {
      BadRequest("invalid argument for rev-list: "+ arg)
    }
  }

  lazy val argPattern = {
    val dots = "\\.{2,3}"
    val wdp = "[\\w\\d]+"
    val branch = wdp+"(((\\.?/?)|(/?\\.?))"+wdp+")*" // branch name or sha hash
    val rev = "("+branch+"((\\^*\\d*)|(~*\\d*)))" // followed by ^n or ~n
    val revNeg = "(\\^?"+rev+")"
    ("("+rev+dots+rev+")|("+revNeg+"(\\s*"+revNeg+")*)").r
  }

  def validArg(arg: String) = {
    argPattern.pattern.matcher(arg).matches()
  }
}
