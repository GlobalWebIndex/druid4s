package gwi.druid.client

import com.typesafe.scalalogging.LazyLogging
import gwi.druid.utils.Granularity
import gwi.randagen._
import org.joda.time.{DateTime, DateTimeZone}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, FreeSpec, Ignore, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration._

@Ignore // TODO change to druid on k8s
class DruidIntegrationTestSuite extends FreeSpec with ScalaFutures with Matchers with BeforeAndAfterAll with LazyLogging {
  import SampleEventDefFactory._
  import Samples._

  val nginxUser = sys.env("DRUID_NGINX_USER")
  val nginxPswd = sys.env("DRUID_NGINX_PSWD")

  lazy private val realSampleSize = 10800
  lazy private val sampleSize = 10799

  lazy private val from = new DateTime(2015, 1, 1, 0, 0, 0, DateTimeZone.UTC)
  lazy private val to = new DateTime(2015, 1, 1, 3, 0, 0, DateTimeZone.UTC)

  lazy private val segmentGrn = Granularity.HOUR
  lazy private val intervals = segmentGrn.getIterable(from, to).map(_.toString).toList

  lazy private val brokerClient = DruidClient.forQueryingBroker(s"$nginxUser:$nginxPswd@broker.gwiq-druid-quickstart-s.gwidx.net", 80)(5.seconds, 1.minute)
  lazy private val overlordClient = DruidClient.forIndexing(s"$nginxUser:$nginxPswd@overlord.gwiq-druid-quickstart-s.gwidx.net", 80)(5.seconds, 5.seconds, 1.minute)

  def indexTestData: Unit = {
    logger.info(s"Data generation initialized ...")
    val targetDirPath = "druid4s-test/gwiq"
    val sourceDataBucket = "gwiq-views-t"
    Await.ready(RanDaGen.run(50 * 1024 * 100, realSampleSize, Parallelism(4), JsonEventGenerator, EventConsumer("s3", s"$sourceDataBucket@$targetDirPath", compress = true), SampleEventDefFactory()), 3.seconds)
    val hadoopTask =
      Samples.hadoopTask(
        segmentGrn,
        intervals,
        Granularity.HOUR,
        Granularity.HOUR,
        "yyyy/MM/dd/HH",
        s"s3n://${sys.env("HADOOP_AWS_ACCESS_KEY_ID")}:${sys.env("HADOOP_AWS_SECRET_ACCESS_KEY")}@$sourceDataBucket/$targetDirPath"
      )
    val result = overlordClient.postTask(hadoopTask).get
    logger.info(s"Indexing finished : ${result.status.status}")
    Thread.sleep(6000) // data becomes queryable after a few seconds, it's driven by segments poll duration
    if (result.status.status != TaskStatus.SUCCESS)
      logger.error(s"Indexing failed !!! ${result.errors.mkString("\n", "\n", "\n")}")
    assertResult(TaskStatus.SUCCESS)(result.status.status)
  }

/*
  override def beforeAll() = indexTestData
*/

  "select" in {
    val response = brokerClient.postQuery(rawSelect(intervals), pretty = true).get.get
    val result = response.result
    val events = result.events
    val headEvent = events.head
    assert(response.timestamp.nonEmpty)
    assertResult(3)(result.pagingIdentifiers.size)
    assertResult(sampleSize)(events.length)
    assert(headEvent.segmentId.nonEmpty)
    assert(headEvent.offset == 0)
    assert(headEvent.event.nonEmpty)
  }

  "count" in {
    val response = brokerClient.postQuery(countTimeSeries(intervals), pretty = true).get.get
    assert(response.timestamp.nonEmpty)
    assertResult(realSampleSize)(response.result(countAggName))
  }

  "hll" in {
    val response = brokerClient.postQuery(hllTimeSeries(intervals), pretty = true).get.get
    assert(response.timestamp.nonEmpty)
    val uniqueCount = response.result(uuidHllAggName)
    assert(5350 < uniqueCount && uniqueCount < 5450)
  }

  "sum" in {
    val response = brokerClient.postQuery(sumTimeSeries(intervals), pretty = true).get.get
    assert(response.timestamp.nonEmpty)
    assertResult((0 until realSampleSize).sum)(response.result(idxSumAggName))
  }

  "groupBy" in {
    val responses = brokerClient.postQuery(groupBy(intervals), pretty = true).get
    assertResult(4)(responses.size)
    val purchaseTypes = responses.map(_.event(purchaseFieldName).toString).toSet
    val purchaseCounts = responses.map(_.event(countByPurchaseAggName).toString.toInt).toSet
    assertResult(4)(purchaseTypes.size)
    assertResult(sampleSize)(purchaseCounts.sum)
  }

  "segmentMetadata" in {
    val response = brokerClient.postQuery(segmentMetadata(intervals), pretty = true).get.get
    val segmentColumns = response.columns.keySet
    Set(sectionFieldName, purchaseFieldName, priceSumAggName, idxSumAggName, uuidHllAggName, countryFieldName, countAggName, "__time").foreach { field =>
      assert(segmentColumns.contains(field))
    }
  }

  "topN" in {
    val response = brokerClient.postQuery(topN(intervals), pretty = true).get.get
    assert(response.timestamp.nonEmpty)
    assertResult(4)(response.result.size)
    assertResult(weightedPurchasePMF.map(_._1).toSet)(response.result.map(_(purchaseFieldName)).toSet)
    assertResult(sampleSize)(response.result.map(_(priceSumAggName).toString.toInt).sum)
  }
}
