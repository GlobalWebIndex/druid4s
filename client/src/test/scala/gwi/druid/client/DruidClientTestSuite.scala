package gwi.druid.client

import java.io.File
import java.nio.file.{Files, Paths}

import gwi.randagen._
import org.joda.time.{DateTime, DateTimeZone}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, FreeSpec, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration._

class DruidClientTestSuite extends FreeSpec with ScalaFutures with Matchers with BeforeAndAfterAll with DruidBootstrap {
  import SampleEventDefFactory._
  import Samples._
  implicit val futurePatience = PatienceConfig(timeout =  Span(5, Seconds), interval = Span(200, Millis))

  lazy private val sampleSize = 10800

  lazy private val from = new DateTime(2015, 1, 1, 0, 0, 0, DateTimeZone.UTC)
  lazy private val to = from.plusSeconds(sampleSize)

  lazy private val segmentGrn = Granularity.HOUR
  lazy private val intervals = segmentGrn.getIterable(from, to).map(_.toString).toList

  lazy private val brokerClient = DruidClient.forQueryingBroker("localhost")(10.seconds, 1.minute)
  lazy private val overlordClient = DruidClient.forIndexing("localhost")(1.seconds, 5.seconds, 60.seconds)

  lazy private val targetDirPath = Paths.get("/tmp/druid4s-test")
  targetDirPath.toFile.mkdirs()

  override def beforeAll() = {
    startDruidContainer()
    Thread.sleep(20000) // Druid doesn't boot-up sooner unfortunately
    Await.result(
      RanDaGen.run(50 * 1024 * 1000, sampleSize, Parallelism(4), JsonEventGenerator, FsEventConsumer(targetDirPath, compress = true), SampleEventDefFactory()),
      120.seconds
    )
    val hadoopTask = Samples.hadoopTask(segmentGrn, intervals, Granularity.HOUR, Granularity.HOUR, "yyyy/MM/dd/HH", targetDirPath.toAbsolutePath.toString)
    val result = overlordClient.postTask(hadoopTask).get
    logger.info(s"Indexing finished : ${result.status.status}")
    Thread.sleep(8000) // data becomes queryable after a few seconds, it's driven by segments poll duration
    if (result.status.status != TaskStatus.SUCCESS)
      logger.error(s"Indexing failed !!! ${result.errors.mkString("\n", "\n", "\n")}")
    assertResult(TaskStatus.SUCCESS)(result.status.status)
  }

  override def afterAll() = {
    def deleteDir(dir: File): Unit = {
      if (dir.exists()) {
        dir.listFiles.foreach { f =>
          if (f.isDirectory)
            deleteDir(f)
          else
            f.delete
        }
        Files.delete(dir.toPath)
      }
    }
    deleteDir(new File(s"$targetDirPath/2015"))
    stopDruidContainer()
  }

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
    assertResult(sampleSize)(response.result(countAggName))
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
    assertResult((0 until sampleSize).sum)(response.result(idxSumAggName))
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
