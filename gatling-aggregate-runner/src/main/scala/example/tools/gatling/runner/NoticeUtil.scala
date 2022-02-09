package example.tools.gatling.runner

import org.slf4j.{Logger, LoggerFactory}
import scalaj.http.{Http, HttpResponse}

object NoticeUtil {

  val logger: Logger = LoggerFactory.getLogger(getClass)

  def sendMessageToSlack(incomingWebhook: String,
                         message: String): HttpResponse[String] = {
    Http(incomingWebhook)
      .header("Content-type", "application/json")
      .postData("{\"text\": \"" + message + "\"}")
      .asString
  }

  def sendMessagesToSlack(incomingWebhookUrlOpt: Option[String],
                          message: String): Unit = {
    incomingWebhookUrlOpt match {
      case Some(incomingWebhookUrl) if incomingWebhookUrl.nonEmpty =>
        val response = sendMessageToSlack(incomingWebhookUrl, message)
        logger.info(s"sendMessageToSlack.response = $response")
      case _ =>
    }
  }
}
