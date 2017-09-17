name := """amazebot"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  "org.antlr" % "antlr-runtime" % "3.5.2",
  "org.codehaus.janino" % "janino" % "3.0.7",
  "org.ow2.asm" % "asm-debug-all" % "5.2",
  "org.python" % "jython-standalone" % "2.7.1",
  ws
)

libraryDependencies += scalaVersion("org.scala-lang" % "scala-compiler" % _ ).value

lazy val elmMake = taskKey[Unit]("elm-make")

elmMake := {
  Seq(
    "bash", "-c",
    "elm-make app/assets/javascripts/Main.elm " +
    "--output public/javascripts/main.js " +
    "--yes --warn"
  ).! match {
    case 0 =>
      streams.value.log.success("elm-make completed.")

    case 127 =>
      streams.value.log.warn("elm-make not found in PATH. Skipping Elm build.")

    case status =>
      throw new IllegalArgumentException(
        s"elm-make failed with non-zero exit ${status}"
      )
  }
}

(compile in Compile) := (compile in Compile).dependsOn(elmMake).value

