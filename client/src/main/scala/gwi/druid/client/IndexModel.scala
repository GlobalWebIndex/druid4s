package gwi.druid.client

import gwi.druid.client.DruidClient.Segment

case class TimestampSpec(column: String, format: Option[String] = Option.empty)
case class DimensionsSpec(dimensions: List[String], dimensionExclusions: List[String], spatialDimensions: List[String])

sealed trait ParseSpec {
  def format: String
  def timestampSpec: TimestampSpec
  def dimensionsSpec: DimensionsSpec
}
case class TimeAndDimsParseSpec(format: String, timestampSpec: TimestampSpec, dimensionsSpec: DimensionsSpec) extends ParseSpec
case class DelimitedParseSpec(
         format: String,
         timestampSpec: TimestampSpec,
         dimensionsSpec: DimensionsSpec,
         columns: List[String],
         delimiter: String,
         hasHeaderRow: Boolean,
         skipHeaderRows: Int,
         listDelimiter: Option[String] = Option.empty
      ) extends ParseSpec
case class JsonParseSpec(format: String, timestampSpec: TimestampSpec, dimensionsSpec: DimensionsSpec) extends ParseSpec
object ParseSpec {
  def timeAndDims(timestampSpec: TimestampSpec, dimensionsSpec: DimensionsSpec): TimeAndDimsParseSpec =
    TimeAndDimsParseSpec("timeAndDims", timestampSpec, dimensionsSpec)
  def json(timestampSpec: TimestampSpec, dimensionsSpec: DimensionsSpec): JsonParseSpec =
    JsonParseSpec("json", timestampSpec, dimensionsSpec)
  def jsonLowerCase(timestampSpec: TimestampSpec, dimensionsSpec: DimensionsSpec): JsonParseSpec =
    JsonParseSpec("jsonLowercase", timestampSpec, dimensionsSpec)
  def csv(timestampSpec: TimestampSpec, dimensionsSpec: DimensionsSpec, columns: List[String], hasHeaderRow: Boolean, skipHeaderRows: Int, listDelimiter: Option[String] = Option.empty): DelimitedParseSpec =
    DelimitedParseSpec("csv", timestampSpec, dimensionsSpec, columns, ",", hasHeaderRow, skipHeaderRows, listDelimiter)
  def tsv(timestampSpec: TimestampSpec, dimensionsSpec: DimensionsSpec, columns: List[String], hasHeaderRow: Boolean, skipHeaderRows: Int, listDelimiter: Option[String] = Option.empty): DelimitedParseSpec =
    DelimitedParseSpec("tsv", timestampSpec, dimensionsSpec, columns, "\t", hasHeaderRow, skipHeaderRows, listDelimiter)
}

sealed trait Parser {
  def `type`: String
  def parseSpec: ParseSpec
}
case class NoopParser(`type`: String, parseSpec: ParseSpec) extends Parser
case class StringParser(`type`: String, parseSpec: ParseSpec) extends Parser
case class HadoopyStringParser(`type`: String, parseSpec: ParseSpec) extends Parser
case class ProtobufParser(`type`: String, parseSpec: ParseSpec) extends Parser
object Parser {
  def noop(parseSpec: ParseSpec): NoopParser = NoopParser("noop", parseSpec)
  def string(parseSpec: ParseSpec): StringParser = StringParser("string", parseSpec)
  def hadoopyString(parseSpec: ParseSpec): HadoopyStringParser = HadoopyStringParser("hadoopyString", parseSpec)
  def protobuf(parseSpec: ParseSpec): ProtobufParser = ProtobufParser("protobuf", parseSpec)
}


sealed trait GranularitySpec {
  def `type`: String
  def queryGranularity: Option[String]
  def intervals: List[String]
  def rollup: Boolean
}

case class ArbitraryGranularitySpec(`type`: String, intervals: List[String], rollup: Boolean = true, queryGranularity: Option[String] = Option.empty) extends GranularitySpec
case class UniformGranularitySpec(`type`: String, intervals: List[String], rollup: Boolean = true, segmentGranularity: Option[String] = Option.empty, queryGranularity: Option[String] = Option.empty) extends GranularitySpec

object GranularitySpec {
  def uniform(intervals: List[String], rollup: Boolean = true, segmentGranularity: Option[String] = Option.empty, queryGranularity: Option[String] = Option.empty): GranularitySpec =
    UniformGranularitySpec("uniform", intervals, rollup, segmentGranularity, queryGranularity)
  def arbitrary(intervals: List[String], rollup: Boolean = true, queryGranularity: Option[String] = Option.empty): GranularitySpec =
    ArbitraryGranularitySpec("arbitrary", intervals, rollup, queryGranularity)
}

case class DataSchema(
         dataSource: String,
         parser: Parser,
         metricsSpec: List[Aggregation],
         granularitySpec: GranularitySpec
     )

sealed trait InputSpec {
  def `type`: String
}
case class StaticInputSpec(`type`: String, paths: List[String]) extends InputSpec
case class GranularityInputSpec(`type`: String, dataGranularity: String, inputPath: String, filePattern: String, pathFormat: Option[String] = Option.empty) extends InputSpec
case class DataSourceInputSpec(`type`: String, ingestionSpec: IngestionUpdateSpec, maxSplitSize: Option[Int]) extends InputSpec
object InputSpec {
  def static(paths: List[String]): StaticInputSpec =
    StaticInputSpec("static", paths)
  def granularity(dataGranularity: String, inputPath: String, filePattern: String, pathFormat: Option[String] = Option.empty): GranularityInputSpec =
    GranularityInputSpec("granularity", dataGranularity, inputPath, filePattern, pathFormat)
  def dataSource(ingestionSpec: IngestionUpdateSpec, maxSplitSize: Option[Int] = Option.empty): DataSourceInputSpec =
    DataSourceInputSpec("dataSource", ingestionSpec, maxSplitSize)
}

// TODO https://github.com/druid-io/druid/blob/master/docs/content/ingestion/firehose.md
case class Firehose(`type`: String, baseDir: String, filter: String)

sealed trait IoConfig {
  def `type`: String
}
case class HadoopIoConfig(`type`: String, inputSpec: InputSpec) extends IoConfig
case class IndexIoConfig(`type`: String, firehose: Firehose) extends IoConfig
object IoConfig {
  def hadoop[S <: InputSpec](inputSpec: S): HadoopIoConfig = HadoopIoConfig("hadoop", inputSpec)
  def index(firehose: Firehose): IndexIoConfig = IndexIoConfig("index", firehose)
}

sealed trait PartitionsSpec {
  def `type`: String
}
case class HashBasedRowPartitioning(`type`: String, targetPartitionSize: Int) extends PartitionsSpec
case class HashBasedShardPartitioning(`type`: String, numShards: Int) extends PartitionsSpec
case class SingleDimensionPartitioning(
        `type`: String,
        targetPartitionSize: Int,
        maxPartitionSize: Option[Int] = Option.empty,
        partitionDimension: Option[String] = Option.empty,
        assumeGrouped: Option[Boolean] = Option.empty
      ) extends PartitionsSpec
object PartitionsSpec {
  def hashBasedByRows(targetPartitionSize: Int): HashBasedRowPartitioning = HashBasedRowPartitioning("hashed", targetPartitionSize)
  def hashBasedByShards(numShards: Int): HashBasedShardPartitioning = HashBasedShardPartitioning("hashed", numShards)
  def dimensionBased(targetPartitionSize: Int, maxPartitionSize: Option[Int] = Option.empty, partitionDimension: Option[String] = Option.empty, assumeGrouped: Option[Boolean] = Option.empty): SingleDimensionPartitioning =
    SingleDimensionPartitioning("dimension", targetPartitionSize, maxPartitionSize, partitionDimension, assumeGrouped)
}

case class TuningConfig(
         `type`: String,
         workingPath: Option[String] = Option.empty,
         version: Option[String] = Option.empty,
         partitionsSpec: Option[PartitionsSpec] = Option.empty,
         maxRowsInMemory: Option[Int] = Option.empty,
         leaveIntermediate: Option[Boolean] = Option.empty,
         cleanupOnFailure: Option[Boolean] = Option.empty,
         overwriteFiles: Option[Boolean] = Option.empty,
         ignoreInvalidRows: Option[Boolean] = Option.empty,
         combineText: Option[Boolean] = Option.empty,
         useCombiner: Option[Boolean] = Option.empty,
         jobProperties: Map[String, String] = Map.empty,
         buildV9Directly: Option[Boolean] = Option.empty,
         numBackgroundPersistThreads: Option[Int] = Option.empty
      )
object TuningConfig {
  val hadoopType = "hadoop"
  val indexType = "index"
}

case class IngestionSpec(dataSchema: DataSchema, ioConfig: IoConfig, tuningConfig: Option[TuningConfig] = Option.empty)
case class IngestionUpdateSpec(
        dataSource: String,
        intervals: List[String],
        segments: Option[List[Segment]] = Option.empty,
        filter: Option[Filter] = Option.empty,
        dimensions: Option[List[String]] = Option.empty,
        metrics: Option[List[String]] = Option.empty,
        ignoreWhenNoSegments: Option[Boolean] = Option.empty
      )

case class IndexTask(`type`: String, spec: IngestionSpec)
object IndexTask {
  val hadoopType = "index_hadoop"
  val indexType = "index"
}

case class Location(host: String, port: Int, tlsPort: Int)

case class IndexTaskResponse(task: String)
case class TaskStatus(
  id: String,
  statusCode: String,
  status: Option[String],
  runnerStatusCode: String,
  `type`: String,
  dataSource: String,
  groupId: Option[String] = Option.empty,
  location: Location,
  duration: Int,
  createdTime: String,
  queueInsertionTime: String,
  errorMsg: Option[String] = Option.empty
)
object TaskStatus {
  val SUCCESS = "SUCCESS"
  val FAILED = "FAILED"
  val RUNNING = "RUNNING"
  val PENDING = "PENDING"
  val WAITING = "WAITING"
}
case class IndexTaskStatusResponse(task: String, status: TaskStatus)