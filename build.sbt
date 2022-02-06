import com.typesafe.sbt.SbtNativePackager.autoImport._
import com.typesafe.sbt.packager.archetypes.scripts.BashStartScriptPlugin.autoImport._
import com.typesafe.sbt.packager.docker.Cmd
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport._
import sbt.Keys._
import sbt._
import Settings._

lazy val `gatling-test` = (project in file("gatling-test"))
  .enablePlugins(GatlingPlugin)
  .settings(gatlingBaseSettings)
  .settings(
    libraryDependencies ++= Seq(
      "io.gatling.highcharts" % "gatling-charts-highcharts" % gatlingVersion,
      "io.gatling" % "gatling-test-framework" % gatlingVersion,
      "com.amazonaws" % "aws-java-sdk-core" % awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-s3" % awsSdkVersion,
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion
    )
  )
  .settings(
    addArtifact(GatlingIt / packageBin / artifact, GatlingIt / packageBin)
  )

lazy val `gatling-runner` = (project in file("gatling-runner"))
  .enablePlugins(JavaAppPackaging, EcrPlugin)
  .settings(gatlingBaseSettings)
  .settings(gatlingRunnerEcrSettings)
  .settings(
    libraryDependencies ++= Seq(
      "io.gatling" % "gatling-app" % gatlingVersion,
      "com.amazonaws" % "aws-java-sdk-core" % awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-s3" % awsSdkVersion
    ),
    Compile / bashScriptDefines / mainClass := Some(
      "example.tools.gatling.runner.Runner"
    ),
    dockerBaseImage := "openjdk:8",
    Docker / packageName := "gatling-runner",
    dockerUpdateLatest := true,
    dockerCommands ++= Seq(
      Cmd("USER", "root"),
      Cmd("RUN", "mkdir /var/log/gatling"),
      Cmd("RUN", "chown daemon:daemon /var/log/gatling"),
      Cmd("ENV", "GATLING_RESULT_DIR=/var/log/gatling")
    )
  )
  .dependsOn(`gatling-test` % "compile->gatling-it")

lazy val `gatling-s3-reporter` = (project in file("gatling-s3-reporter"))
  .settings(name := "gatling-s3-reporter")

lazy val `gatling-aggregate-runner` =
  (project in file("gatling-aggregate-runner"))
    .enablePlugins(JavaAppPackaging, EcrPlugin)
    .settings(gatlingBaseSettings)
    .settings(gatlingAggregateRunnerEcrSettings)
    .settings(gatlingAggregateRunTaskSettings)
    .settings(
      Compile / bashScriptDefines / mainClass := Some(
        "example.tools.gatling.runner.Runner"
      ),
      dockerBaseImage := "openjdk:8",
      Docker / packageName := "gatling-aggregate-runner",
      dockerUpdateLatest := true,
      libraryDependencies ++= Seq(
        "org.slf4j" % "slf4j-api" % "1.7.26",
        "ch.qos.logback" % "logback-classic" % "1.2.3",
        "org.codehaus.janino" % "janino" % "3.0.6",
        "com.iheart" %% "ficus" % "1.5.1",
        "com.github.j5ik2o" %% "reactive-aws-ecs-core" % "1.2.6",
        "org.scalaj" %% "scalaj-http" % "2.4.2"
      )
    )

val root =
  (project in file("."))
    .settings(baseSettings)
    .settings(publishArtifact := false)
    .aggregate(
      `gatling-test`,
      `gatling-runner`,
      `gatling-s3-reporter`,
      `gatling-aggregate-runner`
    )
