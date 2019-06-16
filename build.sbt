addSbtPlugin("org.lyranthe.sbt" % "partial-unification" % "1.1.2")

name := "cacher"

version := "0.1"

scalaVersion := "2.12.0"

libraryDependencies += "dev.zio" %% "zio" % "1.0.0-RC8-5"
libraryDependencies += "org.typelevel" %% "cats-effect" % "1.3.1"
libraryDependencies += "dev.zio" %% "zio-interop-cats" % "1.0.0-RC8-5"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.5" % "test"