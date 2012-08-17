package models

import play.api.Logger

import anorm._
import anorm.SqlParser._

import play.api.db._
import play.api.Play.current

case class Branch(name: String, lastKnownHead: String)

object Branch {
  private val branchParser = {
    get[String]("name") ~
    get[String]("lastKnownHead") map {
      case nme ~ sha =>
        Branch(nme, sha)
    }
  }
  
  def allBranches: List[Branch] = {
    DB.withConnection { implicit c =>
      SQL("select * from branch").as(branchParser *)
    }
  }
  
  def setLastKnownHead(branch: Branch, sha: String) {
    DB.withConnection { implicit c =>
      SQL("update branch set lastKnownHead={sha} where name={nme}").on(
          'sha -> sha,
          'nme -> branch.name
          ).executeUpdate()
    }
  }
}
