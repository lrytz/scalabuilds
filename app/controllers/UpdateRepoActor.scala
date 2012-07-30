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

  def appStateAwait: UpdateState = {
    Await.result((upAct ? "state").mapTo[UpdateState], dur)
  }
  
  def submitUpdateTaskAwait() = {
    Await.result((upAct ? "update").mapTo[Boolean], dur)
  }
}



class RefreshBuildsActor extends Actor {
  def receive = {
    case "refresh" =>
      Logger.info("refreshing builds")
      Application.doRefreshAll()
  }
}


case class BuildTask(sha: String, action: () => Unit)
case class BuildTaskDone(sha: String)

class BuildTasksDispatcher extends Actor {
  
  /**
   * Tasks to be executed; tehre is always at most one task per commit hash in
   * this queue.
   */
  private var taskQueue: imm.Queue[BuildTask] = imm.Queue()
  
  private def enqueue(t: BuildTask) { taskQueue = taskQueue.enqueue(t) }
  private def remove(sha: String) { taskQueue = taskQueue.filterNot(_.sha == sha) }

  private def freeFor(sha: String) = taskQueue.forall(_.sha != sha)
  
  private def run(t: BuildTask) {
    RunAsync(t.action(), self ! BuildTaskDone(t.sha))
  }

  def free: Receive = {
    case t: BuildTask =>
      context.become(busy)
      enqueue(t)
      run(t)
      sender ! true

    case "busyList" =>
      sender ! Set()
  }
  
  def busy: Receive = {
    case t: BuildTask =>
      if (freeFor(t.sha)) {
        enqueue(t)
        sender ! true
      } else {
        sender ! false
      }
      
    case BuildTaskDone(sha) =>
      remove(sha)
      if (taskQueue.isEmpty) {
        context.become(free)
      } else {
        run(taskQueue.head)
      }

    case "busyList" =>
      val r: Set[String] = taskQueue.map(_.sha)(scala.collection.breakOut)
      sender ! r
  }
  
  def receive = free
}

object BuildTasksDispatcher {
  implicit val timeout = Timeout(5 seconds)
  import timeout.{duration => dur}
  import Application.{buildTasksDispatcherActor => dispatcher}

  def busyListAwait = {
    Await.result((dispatcher ? "busyList").mapTo[imm.Set[String]], dur)
  }

  def submitBuildTaskAwait(sha: String, action: => Unit) = {
    Await.result((dispatcher ? BuildTask(sha, () => action)).mapTo[Boolean], dur)
  }
  
  def submitBuildTaskFuture(sha: String, action: => Unit) = {
    (dispatcher ? BuildTask(sha, () => action)).mapTo[Boolean]
  }
}
