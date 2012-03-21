package controllers

import play.api.Logger
import akka.actor.Actor

class PollGithubActor extends Actor {
  def receive = {
    case "update" =>
      Logger.info("update actor starting new poll")
      Application.submitUpdateRepoTask()
    case m =>
      Logger.error("poll github actor does not understand message "+ m)
  }
}

class RefreshBuildsActor extends Actor {
  def receive = {
    case "refresh" =>
      Logger.info("refreshing running builds")
      Application.doRefreshAll()
    case m =>
      Logger.error("refresh build actor does not understand message "+ m)
  }
}
