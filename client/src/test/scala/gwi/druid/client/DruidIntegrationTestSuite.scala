package gwi.druid.client

import com.typesafe.scalalogging.LazyLogging
import gwi.druid.utils.Granularity
import gwi.randagen._
import org.joda.time.{DateTime, DateTimeZone, Interval}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, FreeSpec, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration._

class DruidIntegrationTestSuite
    extends FreeSpec
    with ScalaFutures
    with Matchers
    with BeforeAndAfterAll
    with LazyLogging {
  import SampleEventDefFactory._
  import Samples._

  require(sys.env.get("GOOGLE_APPLICATION_CREDENTIALS").nonEmpty,
          "Test won't work without GOOGLE_APPLICATION_CREDENTIALS exported !!!")

  lazy private val sampleSize = 10800

  lazy private val from = new DateTime(2015, 1, 1, 0, 0, 0, DateTimeZone.UTC)
  lazy private val to = new DateTime(2015, 1, 1, 3, 0, 0, DateTimeZone.UTC)

  lazy private val segmentGrn = Granularity.HOUR
  lazy private val intervals =
    segmentGrn.getIterable(from, to).map(_.toString).toList

  val brokerHost = sys.env.getOrElse(
    "BROKER_HOST",
    throw new IllegalStateException(s"BROKER_HOST env var must be defined !!!"))
  val overlordHost = sys.env.getOrElse(
    "OVERLORD_HOST",
    throw new IllegalStateException(s"BROKER_HOST env var must be defined !!!"))
  val coordinatorHost = sys.env.getOrElse(
    "COORDINATOR_HOST",
    throw new IllegalStateException(s"COORDINATOR_HOST env var must be defined !!!"))

  lazy private val brokerClient =
    DruidClient.forQueryingBroker(brokerHost)(5.seconds, 1.minute)
  lazy private val overlordClient =
    DruidClient.forIndexing(overlordHost)(5.seconds, 5.seconds, 3.minute)
  lazy private val coordinatorClient =
    DruidClient.forQueryingCoordinator(coordinatorHost)(10.seconds, 30.seconds)

  require(brokerClient.isHealthy.get, "Broker is not healthy !!!")
  require(overlordClient.isHealthy.get, "Overlord is not healthy !!!")
  require(coordinatorClient.isHealthy.get, "Coordinator is not healthy !!!")

  def indexTestData(): Unit = {
    logger.info(s"Data generation initialized ...")
    val targetDirPath = "druid4s-test"
    val sourceDataBucket = "gwiq-view-s"
    Await.ready(
      RanDaGen.run(50 * 1024 * 100,
                   10800,
                   Parallelism(4),
                   JsonEventGenerator,
                   EventConsumer("gcs",
                                 s"$sourceDataBucket@$targetDirPath",
                                 compress = true),
                   SampleEventDefFactory()),
      3.seconds
    )
    val hadoopTask =
      Samples.hadoopTask(
        segmentGrn,
        intervals,
        Granularity.HOUR,
        Granularity.HOUR,
        "yyyy/MM/dd/HH",
        s"gs://$sourceDataBucket/$targetDirPath"
      )
    val result = overlordClient.postTask(hadoopTask).get
    logger.info(s"Indexing finished : ${result.status.statusCode}")
    if (result.status.statusCode != TaskStatus.SUCCESS)
      logger.error(
        s"Indexing failed !!! ${result.errors.mkString("\n", "\n", "\n")}")
    assertResult(TaskStatus.SUCCESS)(result.status.statusCode)
    Thread.sleep(8000) // data becomes queryable after a while, depending on segments poll duration
  }

  override def beforeAll(): Unit = indexTestData()

  "scan" in {
    val response = brokerClient.postQuery(rawScan(intervals), pretty = true).get.get
    val events = response.events
    val headEvent = events.head
    assert(response.segmentId.nonEmpty)
    assertResult(sampleSize / intervals.size)(events.length)
    assert(headEvent.nonEmpty)
  }

  "count" in {
    val response =
      brokerClient.postQuery(countTimeSeries(intervals), pretty = true).get.get
    assert(response.timestamp.nonEmpty)
    assertResult(10800)(response.result(countAggName))
  }

  "hll" in {
    val response =
      brokerClient.postQuery(hllTimeSeries(intervals), pretty = true).get.get
    assert(response.timestamp.nonEmpty)
    val uniqueCount = response.result(uuidHllAggName)
    assert(5350d < uniqueCount && uniqueCount < 5450d)
  }

  "sum" in {
    val response =
      brokerClient.postQuery(sumTimeSeries(intervals), pretty = true).get.get
    assert(response.timestamp.nonEmpty)
    assertResult((0 until 10800).sum)(response.result(idxSumAggName))
  }

  "groupBy" in {
    val responses =
      brokerClient.postQuery(groupBy(intervals), pretty = true).get
    assertResult(4)(responses.size)
    val purchaseTypes = responses.map(_.event(purchaseFieldName).toString).toSet
    val purchaseCounts =
      responses.map(_.event(countByPurchaseAggName).toString.toInt).toSet
    assertResult(4)(purchaseTypes.size)
    assertResult(sampleSize)(purchaseCounts.sum)
  }

  "segmentMetadata" in {
    val response =
      brokerClient.postQuery(segmentMetadata(intervals), pretty = true).get.get
    val segmentColumns = response.columns.keySet
    Set(sectionFieldName,
        purchaseFieldName,
        priceSumAggName,
        idxSumAggName,
        uuidHllAggName,
        countryFieldName,
        countAggName,
        "__time").foreach { field =>
      assert(segmentColumns.contains(field))
    }
  }

  "topN" in {
    val response =
      brokerClient.postQuery(topN(intervals), pretty = true).get.get
    assert(response.timestamp.nonEmpty)
    assertResult(4)(response.result.size)
    assertResult(weightedPurchasePMF.map(_._1).toSet)(
      response.result.map(_(purchaseFieldName)).toSet)
    assertResult(sampleSize)(
      response.result.map(_(priceSumAggName).toString.toInt).sum)
  }

  "missing intervals" in {
    val expectedResult = new Interval(to, to.plusHours(1))
    val response =
      coordinatorClient.listMissingIntervals(new Interval(from, to.plusHours(1)), Granularity.HOUR, "gwiq")
    response.get.get shouldBe Vector(expectedResult.toString)
  }
}
