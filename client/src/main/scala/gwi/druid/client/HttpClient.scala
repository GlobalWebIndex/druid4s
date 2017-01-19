package gwi.druid.client

import org.slf4j.LoggerFactory

import scala.util.{Failure, Success, Try}
import scalaj.http.{Http, HttpRequest}

case class HttpClientException(msg: String, statusCode: Int, optCause: Option[Throwable] = None) extends Exception(msg, optCause.orNull)

object HttpClient {
  private val logger = LoggerFactory.getLogger(getClass.getName)

  def request(url: String, extraAttempts: Int = 0, recoverySleep: Int = 5000)(buildRequest: HttpRequest => HttpRequest): Try[String] = {
    val redirectCodes = Set(301, 302, 303, 305, 307)
    def executeRepeatedly(url: String, remainingAttempts: Int): Try[String] = {
      Try(buildRequest(Http(url)).asString) match {
        case f@Failure(ex) =>
          Failure(ex)
        case Success(r) if redirectCodes.contains(r.code) && r.location.isDefined && remainingAttempts > 0 =>
          val newUrl = r.location.get
          logger.info(s"Redirecting request to $newUrl")
          executeRepeatedly(newUrl, remainingAttempts-1)
        case Success(r) if r.code == 200 =>
          Success(r.body)
        case Success(r) if remainingAttempts > 0 =>
          logger.error(s"Server unreachable due to ${r.code} ${r.statusLine}, sleeping for 5 seconds and trying again...")
          Thread.sleep(recoverySleep)
          executeRepeatedly(url, remainingAttempts-1)
        case Success(r) =>
          Failure(HttpClientException(s"Http connection to $url failed with code ${r.code} and status : '${r.statusLine}' despite of repeated attempts", r.code))
      }
    }
    executeRepeatedly(url, extraAttempts) match {
      case Failure(ex) =>
        Failure(ex)
      case Success(r) =>
        Success(r)
    }
  }

}
