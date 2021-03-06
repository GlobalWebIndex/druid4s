package gwi.druid.client

import gwi.druid.utils.Granularity
import gwi.randagen.SampleEventDefFactory

import scala.util.{Failure, Success, Try}

object Samples {

  import SampleEventDefFactory._

  val dataSource          = "gwiq"
  val queryGranularityALL = "all"

  val idxSumAggName          = "indexSum"
  val priceSumAggName        = "priceSum"
  val countAggName           = "count"
  val countByPurchaseAggName = "countByPurchase"
  val uuidHllAggName         = "uuidHll"

  def hadoopTask(
      segmentGrn: Granularity,
      segmentIntervals: List[String],
      queryGrn: Granularity,
      dataGrn: Granularity,
      pathFormat: String,
      inputPath: String
  ): IndexTask =
    IndexTask(
      IndexTask.hadoopType,
      IngestionSpec(
        DataSchema(
          dataSource,
          Parser.hadoopyString(
            ParseSpec.json(
              TimestampSpec(timeFieldName),
              DimensionsSpec(List.empty, List(timeFieldName, uuidFieldName, idxFieldName, priceFieldName), List.empty)
            )
          ),
          List(
            Aggregation.count(countAggName),
            Aggregation.hll(uuidHllAggName, uuidFieldName),
            Aggregation.longSum(idxSumAggName, idxFieldName),
            Aggregation.doubleSum(priceSumAggName, priceFieldName)
          ),
          GranularitySpec.uniform(segmentIntervals, rollup = true, Some(segmentGrn.toString), Some(queryGrn.toString))
        ),
        IoConfig.hadoop(InputSpec.granularity(dataGrn.toString, inputPath, ".*json\\.gz", Some(pathFormat))),
        Some(
          TuningConfig(
            TuningConfig.hadoopType
          )
        )
      )
    )

  /** Note that count aggregation must be longSummed otherwise you'd get segment metadata size - very peculiar */
  def countTimeSeries(intervals: List[String], granularity: String = queryGranularityALL): TimeSeriesQuery =
    Query.timeSeries(dataSource, granularity, intervals, List(Aggregation.longSum(countAggName, countAggName)))

  def rawSelect(intervals: List[String], granularity: String = queryGranularityALL): SelectQuery =
    Query.select(dataSource, granularity, intervals, PagingSpec(Map.empty, 20 * 1000))

  def rawScan(intervals: List[String]): ScanQuery =
    Query.scan(dataSource, intervals)

  def hllTimeSeries(intervals: List[String], granularity: String = queryGranularityALL): TimeSeriesQuery =
    Query.timeSeries(dataSource, granularity, intervals, List(Aggregation.hll(uuidHllAggName, uuidHllAggName)))

  def sumTimeSeries(intervals: List[String], granularity: String = queryGranularityALL): TimeSeriesQuery =
    Query.timeSeries(dataSource, granularity, intervals, List(Aggregation.longSum(idxSumAggName, idxSumAggName)))

  def groupBy(intervals: List[String], granularity: String = queryGranularityALL): GroupByQuery =
    Query.groupBy(dataSource, granularity, intervals, List(Aggregation.count(countByPurchaseAggName)), List(purchaseFieldName))

  def segmentMetadata(intervals: List[String], granularity: String = queryGranularityALL): SegmentMetadataQuery =
    Query.segmentMetadata(dataSource, intervals)

  def topN(intervals: List[String], granularity: String = queryGranularityALL): TopNQuery =
    Query.topN(dataSource, granularity, intervals, List(Aggregation.count(priceSumAggName)), purchaseFieldName, TopNMetric.numeric(priceSumAggName), 5)

  def deleteSegmentsIn(dataSource: String, interval: String, coordinatorIp: String): Unit = {
    import scala.concurrent.duration._
    val client =
      DruidClient
        .forQueryingCoordinator(coordinatorIp, headers = Seq.empty)(5.seconds, 10.seconds)

    def deleteSegments(): Unit =
      client
        .listOverlappingSegments(dataSource, List(interval))
        .get
        .map(_.identifier)
        .foreach { identifier =>
          client.deleteSegment(dataSource, identifier)
          println(identifier)
        }

    println("Deleting segments ...")
    Try(deleteSegments()).flatMap { _ =>
      println("Waiting for coordinator to notice and process deleted segments ...")
      Thread.sleep(125 * 1000)
      client.deleteInterval(dataSource, interval)
    } match {
      case Success(response) =>
        println(s"Segments deleted, response :\n$response")
      case Failure(ex) =>
        println("Segment deletion failed")
        throw ex
    }

  }
}
