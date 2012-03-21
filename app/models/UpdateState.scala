package models

import play.api.libs.concurrent.Promise

sealed trait UpdateState

case object Idle extends UpdateState
case class Updating(p: Promise[List[Commit]]) extends UpdateState
