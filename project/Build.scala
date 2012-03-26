import sbt._
import Keys._
import PlayProject._

object ApplicationBuild extends Build {

    val appName         = "scalabuilds"
    val appVersion      = "1.0-SNAPSHOT"

    val appDependencies = Seq(
      // Add your project dependencies here,
      // "org.eclipse.jgit" % "org.eclipse.jgit" % "1.3.0.201202151440-r"
      "postgresql" % "postgresql" % "9.1-901.jdbc4",
      "net.databinder" %% "dispatch-http" % "0.8.8",
      "cc.spray" %%  "spray-json" % "1.1.1",
//      "com.github.scala-incubator.io" %% "scala-io-core" % "0.3.0",
//      "com.github.scala-incubator.io" %% "scala-io-file" % "0.3.0",
      "joda-time" % "joda-time" % "2.1"
    )

    val main = PlayProject(appName, appVersion, appDependencies, mainLang = SCALA).settings(
      // Add your own project settings here      
      // resolvers += "jgit-repo" at "http://download.eclipse.org/jgit/maven"
      resolvers += "spray repo" at "http://repo.spray.cc/"
    )
}
