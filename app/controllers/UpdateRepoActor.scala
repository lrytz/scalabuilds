package controllers

import play.api.Logger

import play.api.libs.concurrent.Akka
import play.api.Play.current

import akka.actor.Props
import akka.actor.Actor

import akka.util.Timeout
import akka.util.duration._

import akka.pattern.ask
import akka.dispatch.Await

import models._

import collection.{immutable => imm}


/**
 * Run `action` asynchronously, ensure that `finish` is executed even in the
 * presence of exceptions.
 */
object RunAsync {
  def apply(action: => Unit, finish: => Unit) {
    Akka.future {
      try {
        action
      } catch { case e =>
        Logger.error(e.getClass.toString +": "+ e.getMessage +"\n"+ e.getStackTraceString)
        throw e
      } finally {
        finish
      }
    }
  }
}


class UpdateRepoActor extends Actor {
  import context._
  
  def free: Receive = {
    case "update" =>
      become(busy)
      RunAsync(Application.doUpdateRepo(), self ! "done")
      sender ! true
      
    case "state" =>
      sender ! Idle
  }

  def busy: Receive = {
    case "update" =>
      sender ! false
      
    case "state" =>
      sender ! Updating
      
    case "done" =>
      become(free)
  }

  def receive = free
}

object UpdateRepoActor {
  implicit val timeout = Timeout(5 seconds)
  import timeout.{duration => dur}
  import Application.{updateRepoActor => upAct}

  def appState: UpdateState = {
    Await.result((upAct ? "state").mapTo[UpdateState], dur)
  }
  
  def submitUpdateTask() = {
    Await.result((upAct ? "update").mapTo[Boolean], dur)
  }
}



class RefreshRunningBuildsActor extends Actor {
  def receive = {
    case "refresh" =>
      Logger.info("refreshing running builds")
      Application.doRefreshAll()
    case m =>
      Logger.error("refresh build actor does not understand message "+ m)
  }
}


case class RequiredBuildTask(sha: String, action: () => Unit)
case class BuildTask(sha: String, action: () => Unit)
case class BuildTaskDone(sha: String)

class BuildTasksDispatcher extends Actor {
  /**
   * Contains the commits for which a task is running
   */
  var busyList: imm.Set[String] = imm.Set()
  var delayedRequiredTasks: imm.Map[String, List[() => Unit]] = Map().withDefaultValue(Nil)
  def delay(t: RequiredBuildTask) {
    delayedRequiredTasks = delayedRequiredTasks.updated(t.sha, t.action :: delayedRequiredTasks(t.sha))
  }
  def popDelayed(sha: String): Option[() => Unit] = {
    val tasks = delayedRequiredTasks(sha)
    if (tasks.isEmpty) {
      None
    } else {
      delayedRequiredTasks = delayedRequiredTasks.updated(sha, tasks.tail)
      Some(tasks.head)
    }
  }

  private def run(sha: String, action: () => Unit) {
    RunAsync(action(), self ! BuildTaskDone(sha))
  }

  def receive = {
    case rt @ RequiredBuildTask(sha, action) =>
      if (busyList(sha)) {
        delay(rt)
        sender ! false
      } else {
        busyList += sha
        run(sha, action)
        sender ! true
      }

    case BuildTask(sha, action) =>
      if (busyList(sha)) {
        sender ! false
      } else {
        busyList += sha
        run(sha, action)
        sender ! true
      }
      
    case BuildTaskDone(sha) =>
      popDelayed(sha) match {
        case Some(action) =>
          run(sha, action)
        case None =>
          busyList -= sha
      }
      
    case "busyList" =>
      sender ! busyList
  }
}

object BuildTasksDispatcher {
  implicit val timeout = Timeout(5 seconds)
  import timeout.{duration => dur}
  import Application.{buildTasksDispatcherActor => dispatcher}

  def busyList = {
    Await.result((dispatcher ? "busyList").mapTo[imm.Set[String]], dur)
  }
  
  def submitBuildTask(sha: String, action: => Unit) = {
    Await.result((dispatcher ? BuildTask(sha, () => action)).mapTo[Boolean], dur)
  }

  def submitRequiredBuildTask(sha: String, action: => Unit) = {
    Await.result((dispatcher ? RequiredBuildTask(sha, () => action)).mapTo[Boolean], dur)
  }
}
