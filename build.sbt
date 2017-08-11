name := """roomba-sim-web-poc"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  "org.codehaus.janino" % "janino" % "3.0.7",
  "org.webjars.bower" % "angular-cookies" % "1.6.5",
  "org.webjars.bower" % "angular-ui-codemirror" % "0.3.0",
  "org.webjars.bower" % "angular-websocket" % "2.0.0",
  ws
)

libraryDependencies <+= scalaVersion("org.scala-lang" % "scala-compiler" % _ )
