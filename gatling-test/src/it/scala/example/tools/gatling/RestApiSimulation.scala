package example.tools.gatling

import com.typesafe.config.ConfigFactory
import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.gatling.http.protocol.HttpProtocolBuilder

import scala.concurrent.duration._

class RestApiSimulation extends Simulation {
  private val config = ConfigFactory.load()
  private val endpoint =
    config.getString("runner.gatling.target-endpoint-base-url")
  private val pauseDuration =
    config.getDuration("runner.gatling.pause-duration").toMillis.millis
  private val numOfUser = config.getInt("runner.gatling.users")
  private val rampDuration =
    config.getDuration("runner.gatling.ramp-duration").toMillis.millis
  private val holdDuration =
    config.getDuration("runner.gatling.hold-duration").toMillis.millis
  private val entireDuration = rampDuration + holdDuration

  private val httpConf: HttpProtocolBuilder =
    http.doNotTrackHeader("1")
      .contentTypeHeader("application/json")
      .acceptEncodingHeader("gzip, deflate")
      .userAgentHeader("cloud-gatling-tools-agent")

  val scn = scenario(getClass.getName)
    .forever {
      pause(pauseDuration).exec(
        http("api-test")
          .get(s"$endpoint")
          .check(status.is(200))
      )
    }

  setUp(scn.inject(rampUsers(numOfUser).during(rampDuration)))
    .protocols(httpConf)
    .maxDuration(entireDuration)
}
