import com.amazonaws.regions.{Region, Regions}
import com.github.j5ik2o.reactive.aws.ecs.EcsAsyncClient
import com.github.j5ik2o.reactive.aws.ecs.implicits._
import com.typesafe.sbt.SbtNativePackager.autoImport.{maintainer, packageName}
import com.typesafe.sbt.packager.archetypes.scripts.BashStartScriptPlugin.autoImport.bashScriptExtraDefines
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.{
  dockerBaseImage,
  dockerUpdateLatest,
  _
}
import sbt.Keys._
import sbt.internal.util.ManagedLogger
import sbt.{CrossVersion, Resolver, settingKey, taskKey, _}
import sbtecr.EcrPlugin.autoImport.{
  localDockerImage,
  login,
  push,
  region,
  repositoryName,
  repositoryTags,
  _
}
import software.amazon.awssdk.services.ecs.model.{AssignPublicIp, Task, _}
import software.amazon.awssdk.services.ecs.{
  EcsAsyncClient => JavaEcsAsyncClient
}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

object Settings {
  val gatlingVersion = "3.7.4"
  val circeVersion = "0.14.1"
  val awsSdkVersion = "1.12.150"
  val akka26Version = "2.6.18"

  val baseSettings =
    Seq(
      version := "1.0.0",
      scalaVersion := "2.13.8",
      scalacOptions ++= {
        Seq(
          "-feature",
          "-deprecation",
          "-unchecked",
          "-encoding",
          "UTF-8",
          "-language:_",
          "-target:jvm-1.8"
        ) ++ {
          CrossVersion.partialVersion(scalaVersion.value) match {
            case Some((2L, scalaMajor)) if scalaMajor >= 12 =>
              Seq.empty
            case Some((2L, scalaMajor)) if scalaMajor <= 11 =>
              Seq("-Yinline-warnings")
          }
        }
      }
    )

  lazy val dockerBaseSettings = Seq(
    //dockerBaseImage := "adoptopenjdk/openjdk8:x86_64-alpine-jdk8u191-b12",
    dockerBaseImage := "openjdk:8",
    dockerUpdateLatest := true,
    bashScriptExtraDefines ++= Seq(
      "addJava -Xms${JVM_HEAP_MIN:-1024m}",
      "addJava -Xmx${JVM_HEAP_MAX:-1024m}",
      "addJava -XX:MaxMetaspaceSize=${JVM_META_MAX:-512M}",
      "addJava ${JVM_GC_OPTIONS:--XX:+UseG1GC}",
      "addJava -Dconfig.resource=${CONFIG_RESOURCE:-application.conf}",
      "addJava -Dakka.remote.startup-timeout=60s",
      "addJava -Dlog4j2.formatMsgNoLookups=true"
    )
  )

  lazy val gatlingBaseSettings = Seq(
    organization := "com.github.gatlingtools",
    version := "1.0.0-SNAPSHOT",
    scalaVersion := "2.13.8",
    scalacOptions ++= Seq(
      "-feature",
      "-deprecation",
      "-unchecked",
      "-encoding",
      "UTF-8"
    )
  )

  lazy val gatlingRunnerEcrSettings = Seq(
    Ecr / region := Region.getRegion(Regions.fromName(
      sys.env.getOrElse("AWS_REGION", "ap-northeast-1")
    )),
    Ecr / repositoryName := "cloud-gatling-tools/gatling-runner",
    Ecr / localDockerImage := (Docker / packageName).value + ":" + (Docker / version).value,
    Ecr / push := ((Ecr / push) dependsOn (Docker / publishLocal, Ecr / login)).value
  )

  lazy val gatlingAggregateRunnerEcrSettings = Seq(
    Ecr / region := Region.getRegion(Regions.fromName(
      sys.env.getOrElse("AWS_REGION", "ap-northeast-1")
    )),
    Ecr / repositoryName := "cloud-gatling-tools/gatling-aggregate-runner",
    Ecr / localDockerImage := (Docker / packageName).value + ":" + (Docker / version).value,
    Ecr / push := ((Ecr / push) dependsOn (Docker / publishLocal, Ecr / login)).value
  )

  val gatling = taskKey[Unit]("gatling")
  val runTask = taskKey[Seq[Task]]("run-task")

  val runTaskEcsClient = settingKey[EcsAsyncClient]("run-task-ecs-client")
  val runTaskAwaitDuration = settingKey[Duration]("run-task-await-duration")
  val runTaskEcsCluster = settingKey[String]("run-task-ecs-cluster")
  val runTaskTaskDefinition = taskKey[String]("run-task-task-definition")
  val runTaskSubnets = settingKey[Seq[String]]("run-task-subnets")
  val runTaskAssignPublicIp =
    settingKey[AssignPublicIp]("run-task-assign-public-ip")
  val runTaskEnvironments =
    taskKey[Map[String, String]]("run-task-environments")
  val runTaskContainerOverrideName =
    settingKey[String]("run-task-container-override-name")

  def getTaskDefinitionName(client: EcsAsyncClient,
                            awaitDuration: Duration,
                            prefix: String): String = {
    def loop(request: ListTaskDefinitionsRequest): Future[String] = {
      client.listTaskDefinitions(request).flatMap { result =>
        if (result.sdkHttpResponse().isSuccessful) {
          result.nextTokenAsScala match {
            case None =>
              Future.successful(result.taskDefinitionArns().asScala.head)
            case Some(nextToken) =>
              val req = ListTaskDefinitionsRequest
                .builder()
                .familyPrefix(prefix)
                .nextToken(nextToken)
                .sort(SortOrder.DESC)
                .maxResults(1)
                .build()
              loop(req)
          }
        } else
          Future.failed(
            new Exception(result.sdkHttpResponse().statusText().asScala.get)
          )
      }
    }
    val req = ListTaskDefinitionsRequest
      .builder()
      .familyPrefix(prefix)
      .sort(SortOrder.DESC)
      .maxResults(1)
      .build()
    Await.result(loop(req), awaitDuration)
  }
  val gatlingAggregateRunTaskSettings = Seq(
    gatling / runTaskEcsClient := {
      val underlying = JavaEcsAsyncClient
        .builder()
        .build()
      EcsAsyncClient(underlying)
    },
    gatling / runTaskEcsCluster := sys.env
      .getOrElse("GATLING_ECS_CLUSTER", "cloud-gatling-tools-ecs"),
    gatling / runTaskTaskDefinition := {
      getTaskDefinitionName(
        client = (gatling / runTaskEcsClient).value,
        awaitDuration = (gatling / runTaskAwaitDuration).value,
        prefix = "cloud-gatling-tools-gatling-aggregate-runner"
      )
    },
    gatling / runTaskAwaitDuration := Duration.Inf,
    gatling / runTaskSubnets := Seq(
      sys.env.getOrElse("GATLING_SUBNET_ID", "subnet-XXXXXXXXX")
    ),
    gatling / runTaskAssignPublicIp := AssignPublicIp.ENABLED,
    gatling / runTaskEnvironments := {
      Map(
        "AWS_REGION" -> sys.env("AWS_REGION"),
        "GATLING_ECS_CLUSTER_NAME" -> (gatling / runTaskEcsCluster).value,
        "GATLING_SUBNET" -> (gatling / runTaskSubnets).value.head,
        "GATLING_TASK_DEFINITION" -> {
          getTaskDefinitionName(
            client = (gatling / runTaskEcsClient).value,
            awaitDuration = (gatling / runTaskAwaitDuration).value,
            prefix = "cloud-gatling-tools-gatling-runner"
          )
        },
        "GATLING_COUNT" -> sys.env("GATLING_COUNT"),
        "GATLING_PAUSE_DURATION" -> sys.env("GATLING_PAUSE_DURATION"),
        "GATLING_RAMP_DURATION" -> sys.env("GATLING_RAMP_DURATION"),
        "GATLING_HOLD_DURATION" -> sys.env("GATLING_HOLD_DURATION"),
        "GATLING_TARGET_ENDPOINT_BASE_URL" -> sys.env(
          "GATLING_TARGET_ENDPOINT_BASE_URL"
        ),
        "GATLING_SIMULATION_CLASS" -> sys.env("GATLING_SIMULATION_CLASS"),
        "GATLING_USERS" -> sys.env("GATLING_USERS"),
        "GATLING_REPORTER_TASK_DEFINITION" -> {
          getTaskDefinitionName(
            client = (gatling / runTaskEcsClient).value,
            awaitDuration = (gatling / runTaskAwaitDuration).value,
            prefix = "cloud-gatling-tools-gatling-s3-reporter"
          )
        },
        "GATLING_BUCKET_NAME" -> sys.env("GATLING_BUCKET_NAME")
      ) ++ sys.env
        .get("GATLING_NOTICE_SLACK_INCOMING_WEBHOOK_URL")
        .map(v => Map("GATLING_NOTICE_SLACK_INCOMING_WEBHOOK_URL" -> v))
        .getOrElse(Map.empty) ++ {
        sys.env
          .get("GATLING_NOTICE_CHATWORK_HOST")
          .map(v => Map("GATLING_NOTICE_CHATWORK_HOST" -> v))
          .getOrElse(Map.empty) ++
          sys.env
            .get("GATLING_NOTICE_CHATWORK_ROOM_ID")
            .map(v => Map("GATLING_NOTICE_CHATWORK_ROOM_ID" -> v))
            .getOrElse(Map.empty) ++
          sys.env
            .get("GATLING_NOTICE_CHATWORK_TOKEN")
            .map(v => Map("GATLING_NOTICE_CHATWORK_TOKEN" -> v))
            .getOrElse(Map.empty)
      }
    },
    gatling / runTaskContainerOverrideName := "gatling-aggregate-runner",
    gatling / runTask := {
      implicit val log = streams.value.log
      val _runTaskEcsClient = (gatling / runTaskEcsClient).value
      val _runTaskEcsCluster = (gatling / runTaskEcsCluster).value
      val _runTaskTaskDefinition = (gatling / runTaskTaskDefinition).value
      val _runTaskSubnets = (gatling / runTaskSubnets).value
      val _runTaskAssignPublicIp = (gatling / runTaskAssignPublicIp).value
      val _runTaskContainerOverrideName =
        (gatling / runTaskContainerOverrideName).value
      val _runTaskEnvironments = (gatling / runTaskEnvironments).value
      log.info("start runGatlingTask")
      val future = runGatlingTask(
        _runTaskEcsClient,
        _runTaskEcsCluster,
        _runTaskTaskDefinition,
        1,
        _runTaskSubnets,
        _runTaskAssignPublicIp,
        _runTaskContainerOverrideName,
        _runTaskEnvironments
      )
      val result = Await.result(future, (gatling / runTaskAwaitDuration).value)
      result.foreach { task: Task =>
        log.info(s"task.arn = ${task.taskArn()}")
      }
      log.info("finish runGatlingTask")
      result
    }
  )

  def runGatlingTask(
    runTaskEcsClient: EcsAsyncClient,
    runTaskEcsCluster: String,
    runTaskTaskDefinition: String,
    runTaskCount: Int,
    runTaskSubnets: Seq[String],
    runTaskAssignPublicIp: AssignPublicIp,
    runTaskContainerOverrideName: String,
    runTaskEnvironments: Map[String, String]
  )(implicit log: ManagedLogger): Future[Seq[Task]] = {
    val runTaskRequest = RunTaskRequest
      .builder()
      .cluster(runTaskEcsCluster)
      .taskDefinition(runTaskTaskDefinition)
      .count(runTaskCount)
      .launchType(LaunchType.FARGATE)
      .networkConfiguration(
        NetworkConfiguration
          .builder()
          .awsvpcConfiguration(
            AwsVpcConfiguration
              .builder()
              .subnets(runTaskSubnets.asJava)
              .assignPublicIp(runTaskAssignPublicIp)
              .build()
          )
          .build()
      )
      .overrides(
        TaskOverride
          .builder()
          .containerOverrides(
            ContainerOverride
              .builder()
              .name(runTaskContainerOverrideName)
              .environment(
                runTaskEnvironments
                  .map {
                    case (k, v) =>
                      KeyValuePair.builder().name(k).value(v).build()
                  }
                  .toSeq
                  .asJava
              )
              .build()
          )
          .build()
      )
      .build()
    runTaskEcsClient.runTask(runTaskRequest).flatMap { result =>
      if (result.sdkHttpResponse().isSuccessful) {
        val tasks = result.tasks().asScala
        Future.successful(tasks)
      } else {
        throw new Exception(
          result.failures().asScala.map(_.toString()).mkString(",")
        )
      }
    }
  }
}
