package gwi.druid.client

import java.util.concurrent.Executors

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.util.{DefaultPrettyPrinter, MinimalPrettyPrinter}
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import gwi.druid.client.DruidClient.{Broker, Coordinator, Overlord, Plyql}
import org.slf4j.LoggerFactory

import scala.annotation.tailrec
import scala.collection.mutable.{ArrayBuffer, ListBuffer}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds
import scala.util.{Failure, Success, Try}
import scalaj.http.HttpRequest

case class DruidClientException(msg: String, status: String, optCause: Option[Throwable] = None) extends Exception(msg, optCause.orNull)
case class IndexingTaskResult(status: TaskStatus, errors: List[String]) {
  override def toString =
  s"""
     |status: $status
     |${errors.mkString("\n")}
   """.stripMargin
}

/**
  * @note that ObjectMapper is thread safe after initialization so it can be reused by multiple threads
  */
object ObjMapper extends ObjectMapper with ScalaObjectMapper {
  setSerializationInclusion(JsonInclude.Include.NON_NULL)
  registerModule(DefaultScalaModule)
  val prettyWriter  = writer(new DefaultPrettyPrinter)
  val miniWriter    = writer(new MinimalPrettyPrinter)
  val mapReader     = readerFor[Map[String, String]]
}

sealed trait DruidClient {
  def forIndexing(ip: String, port: Int = 8090, protocol: String = "http")(connTimeout: FiniteDuration, readTimeout: FiniteDuration, indexingTimeout: FiniteDuration): Overlord
  def forQueryingBroker(ip: String, port: Int = 8082, protocol: String = "http")(connTimeout: FiniteDuration, readTimeout: FiniteDuration): Broker
  def forQueryingCoordinator(ip: String, port: Int = 8081, protocol: String = "http")(connTimeout: FiniteDuration, readTimeout: FiniteDuration): Coordinator
  def forQueryingPlyqlServer(ip: String, port: Int = 8099, protocol: String = "http")(connTimeout: FiniteDuration, readTimeout: FiniteDuration): Plyql
}

object DruidClient extends DruidClient {
  private val logger = LoggerFactory.getLogger("DruidClient")

  private def defaultTimeout(connTimeout: FiniteDuration, readTimeout: FiniteDuration)(req: HttpRequest) =
    req.timeout(connTimeout.toMillis.toInt, readTimeout.toMillis.toInt)

  def forIndexing(ip: String, port: Int = 8090, protocol: String = "http")(connTimeout: FiniteDuration, readTimeout: FiniteDuration, indexingTimeout: FiniteDuration) =
    Overlord(s"$protocol://$ip:$port/druid/indexer/v1/task", indexingTimeout, defaultTimeout(connTimeout, readTimeout))

  def forQueryingBroker(ip: String, port: Int = 8082, protocol: String = "http")(connTimeout: FiniteDuration, readTimeout: FiniteDuration) =
    Broker(s"$protocol://$ip:$port/druid/v2", defaultTimeout(connTimeout, readTimeout))

  def forQueryingCoordinator(ip: String, port: Int = 8081, protocol: String = "http")(connTimeout: FiniteDuration, readTimeout: FiniteDuration) =
    Coordinator(s"$protocol://$ip:$port/druid/coordinator/v1", defaultTimeout(connTimeout, readTimeout))

  def forQueryingPlyqlServer(ip: String, port: Int = 8099, protocol: String = "http")(connTimeout: FiniteDuration, readTimeout: FiniteDuration) =
    Plyql(s"$protocol://$ip:$port/plyql", defaultTimeout(connTimeout, readTimeout))

  case class Overlord private[DruidClient](url: String, indexingTimeout: FiniteDuration, requestWithTimeouts: HttpRequest => HttpRequest) {

    def postTasks(tasks: List[IndexTask], failFast: Boolean = true): List[Try[IndexingTaskResult]] = {
      tasks.foldLeft(new ListBuffer[Try[IndexingTaskResult]]) {
        case (acc, _) if acc.lastOption.exists(_.isFailure) =>
          acc
        case (acc, task) =>
          logger.info(s"Posting ${acc.length + 1}. task from ${tasks.length}")
          acc += postTask(task)
      }.toList
    }

    def postTasksConcurrently(parll: Int, tasks: IndexedSeq[IndexTask], taskFailureRecoverySleep: Int = 3*60*1000, maxTaskFailuresInARowPerThread: Int = 3)
                             (onSuccess: (Int, IndexTask, IndexingTaskResult) => Unit = (_,_,_) => ()): Future[Seq[Try[IndexingTaskResult]]] = {
      implicit val ec = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(parll))
      @tailrec
      def submit(remainingTasks: IndexedSeq[IndexTask], results: List[Try[IndexingTaskResult]]): Vector[Try[IndexingTaskResult]] = {
        remainingTasks match {
          case xs if xs.nonEmpty =>
            results.headOption.filter(_.isFailure).foreach( _ => Thread.sleep(taskFailureRecoverySleep))
            postTask(xs.head, 1, 0) match {
              case s@Success(result) =>
                onSuccess(xs.length, xs.head, result)
                submit(xs.tail, s :: results)
              case result@Failure(ex) if results.takeWhile(_.isFailure).size >= maxTaskFailuresInARowPerThread =>
                logger.error(s"Indexing task crashed $maxTaskFailuresInARowPerThread times already, taking next one from queue !!!", ex)
                submit(xs.tail, result :: results)
              case result@Failure(ex) =>
                logger.error("Indexing task crashed, trying again !!!", ex)
                submit(xs, result :: results)
            }
          case _ =>
            results.toVector
        }
      }

      def partitionTasks = {
        def partitionIntervals(length: Int, partitionCount: Int): IndexedSeq[(Int, Int)] = {
          require(length/partitionCount >= 2, s"Partitioning makes sense only if length/partitionCount is greater or equal to 2 ... $length/$partitionCount is not !!!")
          val partsSizes = ArrayBuffer.fill(Math.min(length, partitionCount))(length / partitionCount)
          (0 until length % partitionCount).foldLeft(partsSizes) { case (acc, idx) =>
            acc.update(idx, acc(idx) + 1)
            acc
          }.foldLeft(ArrayBuffer.empty[(Int,Int)]) {
            case (acc, e) if acc.isEmpty =>
              acc += 0 -> (e-1)
            case (acc, e) =>
              val last = acc.last._2
              acc += (last+1) -> (last+e)
          }.toIndexedSeq
        }

        val taskPartitionIndexes =
          if (parll == 1)
            IndexedSeq(0 -> (tasks.length-1))
          else
            partitionIntervals(tasks.length, parll)
        taskPartitionIndexes.map { case (from,to ) =>
          tasks.slice(from,to+1)
        }
      }

      Future.sequence(
        partitionTasks.map { tasks =>
          Thread.sleep(2000)
          Future(submit(tasks, List.empty))
        }
      ).map(_.flatten)
    }

    def getTaskLog(taskId: String): Try[String] =
      HttpClient.request(s"$url/$taskId/log", 5)(requestWithTimeouts)

    def getTaskStatus(taskId: String): Try[TaskStatus] =
      HttpClient.request(s"$url/$taskId/status", 5)(requestWithTimeouts)
        .flatMap { responseJson =>
          Try(ObjMapper.readValue[IndexTaskStatusResponse](responseJson).status)
            .recoverWith { case ex: Throwable =>
              logger.error(s"Unable to parse status response json:\n$responseJson", ex)
              Failure(ex)
            }
        }

    def postTask(task: IndexTask, extraConnAttempts: Int = 0, extraAttempts: Int = 0, recoveryConnSleep: Int = 10000, recoverySleep: Int = 10000): Try[IndexingTaskResult] = {
      val start = System.currentTimeMillis()
      val jsonTask = ObjMapper.prettyWriter.writeValueAsString(task)
      logger.info(s"Posting indexing task : \n$jsonTask")
      HttpClient.request(url, extraConnAttempts, recoveryConnSleep) { request =>
        requestWithTimeouts(request)
          .postData(jsonTask)
          .header("content-type", "application/json")
      }.flatMap { responseJson =>
        logger.info(s"Parsing response:\n$responseJson")
        Try(ObjMapper.readValue[IndexTaskResponse](responseJson).task)
          .recoverWith { case ex: Throwable =>
            logger.error(s"Unable to parse indexing response json:\n$responseJson", ex)
            Failure(ex)
          }
      }.flatMap { taskId =>
        val successStatusCode = TaskStatus.SUCCESS
        val failedStatusCode = TaskStatus.FAILED
        val deadLine = System.currentTimeMillis() + indexingTimeout.toMillis
        @tailrec
        def awaitIndexingCompletion: Try[IndexingTaskResult] = {
          Thread.sleep(3000)
          getTaskStatus(taskId) match {
            case Failure(ex) =>
              logger.error("Indexing failed ...", ex)
              Failure(ex)
            case Success(s) if s.status == successStatusCode =>
              val status = s.copy(duration = (System.currentTimeMillis() - start).toInt)
              Success(IndexingTaskResult(status, List.empty))
            case Success(s) if s.status == failedStatusCode && extraAttempts > 0 =>
              logger.error(s"Indexing finished with failed status code $failedStatusCode ... repeating ...")
              Thread.sleep(recoverySleep)
              postTask(task, extraAttempts-1)
            case Success(s) if s.status == failedStatusCode =>
              logger.error(s"Indexing finished with failed status code $failedStatusCode ...")
              getTaskLog(taskId).map(_.split("\n")).map { logLines =>
                logger.error(s"Indexing failed due to:${logLines.mkString("\n","\n","\n")}")
                val errorIdx = logLines.indexWhere(_.contains("ERROR"))
                val errorLines = if (errorIdx != -1) logLines.slice(errorIdx, errorIdx + 200).toList else List.empty
                IndexingTaskResult(s.copy(duration = (System.currentTimeMillis() - start).toInt), errorLines)
              }
            case Success(s) if System.currentTimeMillis() < deadLine =>
              awaitIndexingCompletion
            case Success(s) =>
              Failure(DruidClientException(s"Indexing timed out after ${indexingTimeout.toSeconds} seconds with status: $s", s.status))
          }
        }
        awaitIndexingCompletion
      }
    }
  }

  case class Plyql private[DruidClient](url: String, requestWithTimeouts: HttpRequest => HttpRequest) {
    def postQuery(sqlStatement: String): Try[String] = {
      logger.debug(s"Posting sql statement : \n$sqlStatement")
      HttpClient.request(url, 5) { request =>
        requestWithTimeouts(request)
          .postData(ObjMapper.prettyWriter.writeValueAsString(Map("sql" -> sqlStatement)))
          .header("content-type", "application/json")
      }
    }
  }

  case class Broker private[DruidClient](url: String, requestWithTimeouts: HttpRequest => HttpRequest) {
    import ObjMapper._

    /**
      * @return response which is :
      *         - Try in case of possible http and server errors
      *         - Container because all responses are Traversables of 0 to x elements, they mostly contain just one element though
      */
    def postQuery[Q <: Query, R <: Response, C[X]](query: Q, pretty: Boolean = false)(implicit r: ResponseReader[Q,R,C]): Try[C[R]] = {
      val jsonQuery = ObjMapper.prettyWriter.writeValueAsString(query)
      logger.debug(s"Posting query : \n$jsonQuery")
      val newUrl = if (pretty) url + "?pretty" else url
      HttpClient.request(newUrl, 5) { request =>
        requestWithTimeouts(request)
          .postData(jsonQuery)
          .header("content-type", "application/json")
      }.flatMap { response =>
        Try(implicitly[ResponseReader[Q, R, C]].read(response)) match {
          case s@Success(_) => s
          case f@Failure(ex) =>
            logger.error(s"Query response serialization failed : ${response.take(1000)}\n", ex)
            f
        }
      }
    }

    def listMetrics(dataSource: String): Try[List[String]] =
      HttpClient.request(s"$url/datasources/$dataSource/metrics", 5)(requestWithTimeouts).map(readValue[List[String]])

    def listDimensions(dataSource: String): Try[List[String]] =
      HttpClient.request(s"$url/datasources/$dataSource/dimensions", 5)(requestWithTimeouts).map(readValue[List[String]])
  }

  case class IntervalMetadata(size: Int, count: Int)
  case class LoadSpec(`type`: String, bucket: String, key: String, S3Schema: Option[String] = Some("s3n"))
  case class ShardSpec(`type`: String)
  case class Segment(dataSource: String, interval: String, loadSpec: LoadSpec, dimensions: String, metrics: String, shardSpec: ShardSpec, version: String, binaryVersion: Int, size: Int, identifier: String)

  case class Coordinator private[DruidClient](url: String, requestWithTimeouts: HttpRequest => HttpRequest) {
    import ObjMapper._

    /**
      * @return the current leader coordinator of the cluster.
      */
    def getLeader: Try[String] =
      HttpClient.request(s"$url/leader", 5)(requestWithTimeouts)

    /**
      * @return the percentage of segments actually loaded in the cluster versus segments that should be loaded in the cluster.
      */
    def getLoadStatus: Try[Map[String, Double]] =
      HttpClient.request(s"$url/loadstatus", 5)(requestWithTimeouts).map(readValue[Map[String, Double]])

    /**
      * @return the number of segments left to load until segments that should be loaded in the cluster are available for queries. This does not include replication.
      */
    def getLoadStatusSimple: Try[Map[String, Double]] =
      HttpClient.request(s"$url/loadstatus?simple", 5)(requestWithTimeouts).map(readValue[Map[String, Double]])

    /**
      * @return a list of the names of datasources in the cluster
      */
    def listDataSources(includeDisabled: Boolean): Try[List[String]] = {
      val dataSourcesUrl = s"$url/metadata/datasources${Option(includeDisabled).filter(identity).map(_ => "?includeDisabled").getOrElse("")}"
      HttpClient.request(dataSourcesUrl, 5)(requestWithTimeouts).map(readValue[List[String]])
    }

    /**
      * @return full segment metadata for a specific segment as stored in the metadata store.
      */
    def getSegment(dataSource: String, segmentId: String): Try[Segment] =
      HttpClient.request(s"$url/metadata/datasources/$dataSource/segments/$segmentId", 5)(requestWithTimeouts).map(readValue[Segment])

    /**
      * @return a list of all segments for a datasource as stored in the metadata store.
      */
    def listSegmentIds(dataSource: String): Try[Seq[String]] =
      HttpClient.request(s"$url/metadata/datasources/$dataSource/segments", 5)(requestWithTimeouts).map(readValue[Vector[String]])

    /**
      * @return a list of all segments for a datasource with the full segment metadata as stored in the metadata store.
      */
    def listSegments(dataSource: String): Try[Seq[Segment]] =
      HttpClient.request(s"$url/metadata/datasources/$dataSource/segments?full", 5)(requestWithTimeouts).map(readValue[Vector[Segment]])

    /**
      * @param intervals ["2012-01-01T00:00:00.000/2012-01-03T00:00:00.000", "2012-01-05T00:00:00.000/2012-01-07T00:00:00.000"]
      * @return a list of all segments, overlapping with any of given intervals, for a datasource as stored in the metadata store.
      */
    def listOverlappingSegmentIds(dataSource: String, intervals: List[String]) =
      HttpClient.request(s"$url/metadata/datasources/$dataSource/segments", 5) { request =>
          requestWithTimeouts(request)
          .postData(miniWriter.writeValueAsString(intervals))
          .header("content-type", "application/json")
      }.map(readValue[Vector[String]])

    /**
      * @param intervals ["2012-01-01T00:00:00.000/2012-01-03T00:00:00.000", "2012-01-05T00:00:00.000/2012-01-07T00:00:00.000"]
      * @return a list of all segments, overlapping with any of given intervals, for a datasource with the full segment metadata as stored in the metadata store
      */
    def listOverlappingSegments(dataSource: String, intervals: List[String]) =
      HttpClient.request(s"$url/metadata/datasources/$dataSource/segments?full", 5) { request =>
        requestWithTimeouts(request)
          .postData(miniWriter.writeValueAsString(intervals))
          .header("content-type", "application/json")
      }.map(readValue[Vector[Segment]])

    /**
      * @return a set of segment intervals
      * @note that result is optional in case data-source is missing
      */
    def listDataSourceIntervals(dataSource: String): Try[Option[Seq[String]]] =
      HttpClient
        .request(s"$url/datasources/$dataSource/intervals", 5)(requestWithTimeouts)
        .map( intervals => Option(readValue[Vector[String]](intervals))) match {
          case Failure(HttpClientException(msg, statusCode, ex)) if statusCode == 204 || statusCode == 404 =>
            Success(Option.empty)
          case result =>
            result
        }

    /**
      * @return a map of an interval to a JSON object containing the total byte size of segments and number of segments for that interval.
      */
    def listDataSourceIntervalMetadata(dataSource: String): Try[Map[String, IntervalMetadata]] =
      HttpClient.request(s"$url/datasources/$dataSource/intervals?simple", 5)(requestWithTimeouts).map(readValue[Map[String, IntervalMetadata]])

    /**
      * Disables a segment. Note that it takes druid.coordinator.period + druid.manager.segments.pollDuration for changes to reflect
      */
    def deleteSegment(dataSource: String, segmentId: String): Try[String] =
      HttpClient.request(s"$url/datasources/$dataSource/segments/$segmentId", 5)(requestWithTimeouts(_).method("DELETE"))

    /**
      * Runs a Kill task for a given interval and datasource
      */
    def deleteInterval(dataSource: String, interval: String): Try[String] =
      HttpClient.request(s"$url/datasources/$dataSource/intervals/${interval.replace('/', '_')}", 5)(requestWithTimeouts(_).method("DELETE"))

  }
}