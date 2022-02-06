libraryDependencies ++= Seq(
  "com.github.j5ik2o" %% "reactive-aws-ecs-core" % "1.1.3"
)
addSbtPlugin("io.gatling" % "gatling-sbt" % "4.1.2")

addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.9.7")

addSbtPlugin("com.lightbend.sbt" % "sbt-javaagent" % "0.1.6")

addSbtPlugin("com.mintbeans" % "sbt-ecr" % "0.16.0")

addSbtPlugin("io.spray" % "sbt-revolver" % "0.9.1")
