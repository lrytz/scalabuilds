package models

import play.api.libs.concurrent.Promise

sealed trait UpdateState

case object Idle extends UpdateState
case object Updating extends UpdateState
