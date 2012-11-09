package controllers.jenkins

import org.specs2.mutable._

import play.api.test._
import play.api.test.Helpers._

class JenkinsToolsSuite extends Specification {
  
  def app[T](op: => T): T = {
    running(FakeApplication(additionalConfiguration = inMemoryDatabase())) {
      op
    }
  }

  "jobInfoPage" in {
    app {
      "" must have size(0)
    }
  }
}
