package controllers
package revlist

import models.Branch

import dispatch._
import Application.silentHttp

object RevLister {
  def newCommitsIn(branch: Branch) = {
    import dispatch._
    val req = url(Config.revListerUrl + branch.lastKnownHead +".."+ branch.name)
    silentHttp(req >- { res =>
      res match {
        case "" => Nil
        case s  => s.split(",").toList
      }
    })
  }
}
