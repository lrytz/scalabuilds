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

  // pattern for valid rev-list argument. a more complete regex could be found on so:
  // http://stackoverflow.com/questions/12093748/how-do-i-check-for-valid-git-branch-names
  lazy val argPattern = {
    val dots = "\\.{2,3}"
    // valid first characters for branch names
    val startChar = "[\\w,;]"
    // valid in branch name (but not in the end): non-repeated . or / (uses lookahead pattern)
    val partChar = "\\.(?!\\.)|/(?!/)"
    // valid characters in branch name or at the end of a branch name
    val partOrEndChar = "[-\\w,;]"
    // a branch name (or sha hash). the branch characters are an atomic group, so there will not be
    // any backtracking in the characters of a branch name. without that, a non-match as the following
    // will go into a (almost) infinite backtrack sequence: "8b598436f64ca4e980c8a38f642085b4d23e2327.."
    val branch = startChar+"(?>("+partChar+")*"+partOrEndChar+")*" // branch name or sha hash
    // a revision: branch name followed by ^'s or ~'s or ^n or ~n for some number n
    val rev = "("+branch+"((\\^*\\d*)|(~*\\d*)))"
    // a revision or its negation
    val revNeg = "(\\^?"+rev+")"
    // a rev-list argument is either rev..rev or rev...rev or a space-separated sequence of
    // revisions (or negated revisions)
    ("("+rev+dots+rev+")|("+revNeg+"(\\s+"+revNeg+")*)").r
  }

  def validArg(arg: String) = {
    argPattern.pattern.matcher(arg).matches()
  }
}
