package gwi.druid.client

import java.io.File
import java.nio.file.{Files, Paths, StandardOpenOption}
import java.text.{DecimalFormat, DecimalFormatSymbols}
import java.time.temporal.ChronoUnit

import gwi.druid.utils.Granularity
import gwi.randagen._
import org.joda.time.{DateTime, DateTimeZone}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.math.BigDecimal.RoundingMode._
import scala.util.Try

object IndexingBenchmark extends App with DruidBootstrap {

  val dataDir = Paths.get("/tmp/druid4s-test/events")
  val reportDir = Paths.get("/tmp/druid4s-test/bench")

  reportDir.toFile.mkdirs()
  dataDir.toFile.mkdirs()
  cleanUp() // so that druid docker container and 60GB of data don't get left behind on a machine in case of JVM crash
  sys.addShutdownHook(cleanUp())

  // 5 days have 432 millions millis
  runBench("4_4_8",  List(432000,864000), SampleEventDefFactory(ChronoUnit.MILLIS, dynamicMaxSegmentSize = 4, dynamicMaxFieldCount = 4, dynamicDataPointsCount = 8))
  runBench("6_6_16", List(432000,864000), SampleEventDefFactory(ChronoUnit.MILLIS, dynamicMaxSegmentSize = 6, dynamicMaxFieldCount = 6, dynamicDataPointsCount = 16))
  runBench("8_8_32", List(432000,864000), SampleEventDefFactory(ChronoUnit.MILLIS, dynamicMaxSegmentSize = 8, dynamicMaxFieldCount = 8, dynamicDataPointsCount = 32))

  private def cleanUp() = {
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
    stopDruidContainer()
    dataDir.toFile.listFiles.foreach(deleteDir)
    Thread.sleep(5000)
  }

  private def startDruidAndWait() = {
    startDruidContainer()
    Thread.sleep(20000)
  }

  private def runBench(name: String, sizes: List[Int], e: SampleEventDefFactory) = {
    def start(size: Int): IndexingBenchResult = {
      import Samples._

      import scala.concurrent.ExecutionContext.Implicits.global
      val brokerClient = DruidClient.forQueryingBroker("localhost")(10.seconds, 1.minute)
      val overlordClient = DruidClient.forIndexing("localhost")(10.seconds, 1.minute, 12.hours)
      startDruidAndWait()
      val f =
        RanDaGen
          .run(100*1024*1000, size, Parallelism(4), JsonEventGenerator, FsEventConsumer(dataDir, compress = true), e)
          .map { report =>
            println("Sample data generated...")
            val from = new DateTime(2015, 1, 1, 0, 0, 0, DateTimeZone.UTC)
            val to = from.plus(size)
            val segmentGrn = Granularity.HOUR
            val intervals = segmentGrn.getIterable(from, to).map(_.toString).toList
            val tasks = intervals.map( interval => hadoopTask(segmentGrn, List(interval), Granularity.HOUR, Granularity.HOUR, "yyyy/MM/dd/HH", dataDir.toAbsolutePath.toString) )
            val start = System.currentTimeMillis()
            val results = Await.result(overlordClient.postTasksConcurrently(1, tasks.toIndexedSeq)(), 20.hours)
            val took = System.currentTimeMillis() - start
            Thread.sleep(60000)
            val queryResponses =
              List(countTimeSeries(intervals), hllTimeSeries(intervals), sumTimeSeries(intervals))
                .flatMap(brokerClient.postQuery(_, pretty = true).get)

            val benchResult = IndexingBenchResult(size, took, queryResponses, report, results)
            println("Intermediate result for bench of size " + size)
            println(benchResult)
            benchResult
          } andThen {
          case x =>
            cleanUp()
        }
      Await.result(f, 12.hours)
    }
    val result = sizes.map(start).mkString("\n", "\n", "\n")
    Files.write(reportDir.resolve(name), result.getBytes, StandardOpenOption.CREATE)
  }
}

case class IndexingBenchResult(totalSize: Int, tookTotal: Long, queryResponses: List[Response], report: Report, results: Seq[Try[IndexingTaskResult]]) {
  private val formatter = {
    val symbols = DecimalFormatSymbols.getInstance()
    symbols.setGroupingSeparator(' ')
    new DecimalFormat("#,###", symbols)
  }
  override def toString = {
    def scale(value: Double) = BigDecimal(value).setScale(2, HALF_UP)
    val throughputTotal = totalSize / (tookTotal/1000D)
    s"""
       |DATA GENERATOR
       |$report
       |
       |DRUID
       |size : ${formatter.format(totalSize)} events
       |tasks : ${results.size}
       |crashed tasks : ${results.count(_.isFailure)}
       |failed tasks : ${results.filter(_.isSuccess).count(_.get.status.statusCode != TaskStatus.SUCCESS)}
       |took total: ${scale(tookTotal/1000)} s
       |throughput: ${scale(throughputTotal)} events/s
       |query responses: ${queryResponses.mkString("\n", "\n--------------------------------------------------\n", "\n")}
       |errors: ${results.filter(_.isSuccess).flatMap(_.get.errors).mkString("\n")}
    """.stripMargin
  }
}