package gwi.druid.client

/** F.I.L.T.E.R.S **/

sealed trait Filter {
  def `type`: String
}
case class SelectorFilter(`type`: String, dimension: String, value: String) extends Filter
case class RegexFilter(`type`: String, dimension: String, pattern: String) extends Filter
case class JavaScriptFilter(`type`: String, dimension: String, function: String) extends Filter
case class LogicalFilter(`type`: String, fields: List[Filter]) extends Filter
object Filter {
  def selector(dimension: String, value: String): SelectorFilter = SelectorFilter("selector", dimension, value)
  def regex(dimension: String, value: String): RegexFilter = RegexFilter("regex", dimension, value)
  def javascript(dimension: String, function: String): JavaScriptFilter = JavaScriptFilter("javascript", dimension, function)
  def logicalAnd(fields: List[Filter]): LogicalFilter = LogicalFilter("and", fields)
  def logicalOr(fields: List[Filter]): LogicalFilter = LogicalFilter("or", fields)
  def logicalNot(fields: List[Filter]): LogicalFilter = LogicalFilter("not", fields)
}


/** P.O.S.T   A.G.G.R.E.G.A.T.I.O.N.S **/

sealed trait PostAggregation {
  def `type`: String
  def name: String
}
case class ArithmeticPostAggregation(`type`: String, name: String, fn: String, fields: List[PostAggregation], ordering: Option[String] = Option.empty) extends PostAggregation
case class FieldAccessPostAggregation(`type`: String, name: String, fieldName: String) extends PostAggregation
case class ConstantPostAggregation(`type`: String, name: String, value: String) extends PostAggregation
case class JavaScriptPostAggregation(`type`: String, name: String, fieldNames: List[String], function: String) extends PostAggregation
case class HLLPostAggregation(`type`: String, name: String, fieldName: String) extends PostAggregation
object PostAggregation {
  def arithmetic(name: String, fn: String, fields: List[PostAggregation], ordering: Option[String] = Option.empty): ArithmeticPostAggregation =
    ArithmeticPostAggregation("arithmetic", name, fn, fields, ordering)
  def fieldAccess(name: String, fieldName: String): FieldAccessPostAggregation =
    FieldAccessPostAggregation("fieldAccess", name, fieldName)
  def constant(name: String, value: String): ConstantPostAggregation =
    ConstantPostAggregation("constant", name, value)
  def javascript(name: String, fieldNames: List[String], function: String): JavaScriptPostAggregation =
    JavaScriptPostAggregation("javascript", name, fieldNames, function)
  def hll(name: String, fieldName: String): HLLPostAggregation =
    HLLPostAggregation("hyperUniqueCardinality", name, fieldName)
}

/** Q.U.E.R.Y   C.O.N.T.E.X.T **/

case class Context(
        timeout: Option[Int] = Option.empty,
        priority: Option[Int] = Option.empty,
        queryId: Option[String] = Option.empty,
        useCache: Option[Boolean] = Option.empty,
        populateCache: Option[Boolean] = Option.empty,
        bySegment: Option[Boolean] = Option.empty,
        chunkPeriod: Option[Int] = Option.empty
    )

sealed trait Query {
  def queryType: String
  def dataSource: String
  def context: Option[Context]
}

/** T.I.M.E   B.O.U.N.D.A.R.Y   Q.U.E.R.Y **/

case class TimeBoundaryQuery(queryType: String, dataSource: String, bound: Option[String] = Option.empty, context: Option[Context] = Option.empty) extends Query


/** S.E.G.M.E.N.T   M.E.T.A.D.A.T.A   Q.U.E.R.Y **/

sealed trait ToInclude {
  def `type`: String
}
case class IncludeAll(`type`: String) extends ToInclude
case class IncludeNone(`type`: String) extends ToInclude
case class IncludeList(`type`: String, columns: List[String]) extends ToInclude
object ToInclude {
  def all: IncludeAll = IncludeAll("all")
  def none: IncludeNone = IncludeNone("none")
  def list(columns: List[String]): IncludeList = IncludeList("list", columns)
}

case class SegmentMetadataQuery(
             queryType: String,
             dataSource: String,
             intervals: List[String],
             toInclude: Option[ToInclude] = Option.empty,
             merge: Option[Boolean] = Option.empty,
             context: Option[Context] = Option.empty
           ) extends Query
case class SegmentMetadataField(`type`: String, size: Long, cardinality: Option[Int], hasMultipleValues: Option[Boolean], minValue: Option[String], maxValue: Option[String], errorMessage: Option[String])
case class SegmentMetadataResponse(
              id: String,
              intervals: List[String],
              columns: Map[String, SegmentMetadataField],
              size: Long,
              aggregators: Option[Map[String, Aggregation]],
              numRows: Option[Long],
              queryGranularity: Option[String],
              timestampSpec: Option[TimestampSpec],
              rollup: Option[Boolean]
            ) extends Response

/** D.A.T.A  S.O.U.R.C.E  M.E.T.A.D.A.T.A   Q.U.E.R.Y **/

case class DataSourceMetadataQuery(queryType: String, dataSource: String, context: Option[Context] = Option.empty) extends Query
case class DataSourceMetadataResponse(timestamp: String) extends Response


/** T.I.M.E.S.E.R.I.E.S   Q.U.E.R.Y **/

case class TimeSeriesQuery(
          queryType: String,
          dataSource: String,
          granularity: String,
          intervals: List[String],
          aggregations: List[Aggregation],
          filter: Option[Filter] = Option.empty,
          postAggregation: Option[PostAggregation] = Option.empty,
          context: Option[Context] = Option.empty
      ) extends Query
case class TimeSeriesResponse(timestamp: String, result: Map[String, Double]) extends Response

/** G.R.O.U.P.B.Y   Q.U.E.R.Y **/

sealed trait GroupByLimitSpec
case class GroupByDefaultLimit(`type`: String, limit: Int, columns: List[String]) extends GroupByLimitSpec
case class OrderByColumnLimit(dimension: String, direction: String) extends GroupByLimitSpec
object GroupByLimitSpec {
  def default(`type`: String, limit: Int, columns: List[String]): GroupByDefaultLimit = GroupByDefaultLimit(`type`, limit, columns)
  def orderByAsc(dimension: String): OrderByColumnLimit = OrderByColumnLimit(dimension, "ascending")
  def orderByDesc(dimension: String): OrderByColumnLimit = OrderByColumnLimit(dimension, "descending")
}


sealed trait HavingSpec {
  def `type`: String
}
case class NumericHaving(`type`: String, aggregation: String, value: Int) extends HavingSpec
case class LogicalHaving(`type`: String, havingSpecs: List[String]) extends HavingSpec
case class NotHaving(`type`: String, havingSpec: String) extends HavingSpec
object HavingSpec {
  def equalTo(aggregation: String, value: Int): NumericHaving = NumericHaving("equalTo", aggregation: String, value: Int)
  def greaterThan(aggregation: String, value: Int): NumericHaving = NumericHaving("greaterThan", aggregation: String, value: Int)
  def lessThan(aggregation: String, value: Int): NumericHaving = NumericHaving("lessThan", aggregation: String, value: Int)
  def logicalAnd(havingSpecs: List[String]): LogicalHaving = LogicalHaving("and", havingSpecs)
  def logicalOr(havingSpecs: List[String]): LogicalHaving = LogicalHaving("or", havingSpecs)
  def not(havingSpec: String): NotHaving = NotHaving("not", havingSpec)
}

case class GroupByQuery(
         queryType: String,
         dataSource: String,
         granularity: String,
         intervals: List[String],
         aggregations: List[Aggregation],
         dimensions: List[String],
         limitSpec: Option[GroupByLimitSpec] = Option.empty,
         having: Option[HavingSpec] = Option.empty,
         filter: Option[Filter] = Option.empty,
         postAggregation: Option[PostAggregation] = Option.empty,
         context: Option[Context] = Option.empty
      ) extends Query
case class GroupByResponse(timestamp: String, version: String, event: Map[String, Any]) extends Response

/** T.O.P.N   Q.U.E.R.Y **/

// TODO http://druid.io/docs/latest/querying/dimensionspecs.html

sealed trait TopNMetric {
  def `type`: String
}
object TopNMetric {
  def numeric(metric: String): NumericTopNMetric = NumericTopNMetric("numeric", metric)
  def inverted(metric: String): NumericTopNMetric = NumericTopNMetric("inverted", metric)
  def lexicographic(previousStop: Option[String] = Option.empty): AlphaNumericTopNMetric = AlphaNumericTopNMetric("lexicographic", previousStop)
  def alphaNumeric(previousStop: Option[String] = Option.empty): AlphaNumericTopNMetric = AlphaNumericTopNMetric("alphaNumeric", previousStop)
}
case class NumericTopNMetric(`type`: String, metric: String) extends TopNMetric
case class AlphaNumericTopNMetric(`type`: String, previousStop: Option[String] = Option.empty) extends TopNMetric

case class TopNQuery(
        queryType: String,
        dataSource: String,
        granularity: String,
        intervals: List[String],
        aggregations: List[Aggregation],
        dimension: String,
        filter: Option[Filter] = Option.empty,
        postAggregation: Option[PostAggregation] = Option.empty,
        metric: TopNMetric,
        threshold: Int,
        context: Option[Context] = Option.empty
    ) extends Query
case class TopNResponse(timestamp: String, result: IndexedSeq[Map[String, Any]]) extends Response

/** S.C.A.N   Q.U.E.R.Y */
object ScanQuery {
  object Order {
    val ascending = "ascending"
    val descending = "descending"
    val none = "none"
  }
}
case class ScanQuery(
  queryType: String,
  dataSource: String,
  intervals: List[String],
  resultFormat: String,
  batchSize: Option[Int] = Option.empty,
  order: Option[String],
  limit: Option[Int] = Option.empty,
  filter: Option[Filter] = Option.empty,
  columns: List[String] = List.empty,
  context: Option[Context] = Option.empty
) extends Query

case class ScanResponse(segmentId: String, columns: List[String], events: IndexedSeq[Map[String, Any]]) extends Response

/** S.E.L.E.C.T   Q.U.E.R.Y **/

case class PagingSpec(pagingIdentifiers: Map[String, Int], threshold: Int)
case class SelectQuery(
        queryType: String,
        dataSource: String,
        granularity: String,
        intervals: List[String],
        pagingSpec: PagingSpec,
        filter: Option[Filter] = Option.empty,
        dimensions: List[String] = List.empty,
        metrics: List[String] = List.empty,
        context: Option[Context] = Option.empty
    ) extends Query
case class SelectEvent(segmentId: String, offset: Int, event: Map[String, Any])
case class SelectResult(pagingIdentifiers: Map[String, Int], events: IndexedSeq[SelectEvent], dimensions: List[String], metrics: List[String])
case class SelectResponse(timestamp: String, result: SelectResult) extends Response

object Query {
  def timeSeries(
        dataSource: String,
        granularity: String,
        intervals: List[String],
        aggregations: List[Aggregation],
        filter: Option[Filter] = Option.empty,
        postAggregation: Option[PostAggregation] = Option.empty,
        context: Option[Context] = Option.empty
    ): TimeSeriesQuery = TimeSeriesQuery("timeseries", dataSource, granularity, intervals, aggregations, filter, postAggregation, context)

  def groupBy(
       dataSource: String,
       granularity: String,
       intervals: List[String],
       aggregations: List[Aggregation],
       dimensions: List[String],
       limitSpec: Option[GroupByLimitSpec] = Option.empty,
       having: Option[HavingSpec] = Option.empty,
       filter: Option[Filter] = Option.empty,
       postAggregation: Option[PostAggregation] = Option.empty,
       context: Option[Context] = Option.empty
     ): GroupByQuery = GroupByQuery("groupBy", dataSource, granularity, intervals, aggregations, dimensions: List[String], limitSpec, having, filter, postAggregation, context)

  def topN(
       dataSource: String,
       granularity: String,
       intervals: List[String],
       aggregations: List[Aggregation],
       dimension: String,
       metric: TopNMetric,
       threshold: Int,
       filter: Option[Filter] = Option.empty,
       postAggregation: Option[PostAggregation] = Option.empty,
       context: Option[Context] = Option.empty
     ): TopNQuery = TopNQuery("topN", dataSource, granularity, intervals, aggregations, dimension: String, filter, postAggregation, metric, threshold, context)

  def dataSourceMetadata(dataSource: String, context: Option[Context] = Option.empty): DataSourceMetadataQuery =
    DataSourceMetadataQuery("dataSourceMetadata", dataSource, context)

  def segmentMetadata(dataSource: String, intervals: List[String], toInclude: Option[ToInclude] = Option.empty, merge: Option[Boolean] = Option.empty, context: Option[Context] = Option.empty): SegmentMetadataQuery =
    SegmentMetadataQuery("segmentMetadata", dataSource, intervals, toInclude, merge, context)

  def select(
        dataSource: String,
        granularity: String,
        intervals: List[String],
        pagingSpec: PagingSpec,
        filter: Option[Filter] = Option.empty,
        dimensions: List[String] = List.empty,
        metrics: List[String] = List.empty,
        context: Option[Context] = Option.empty
      ): SelectQuery = SelectQuery("select", dataSource, granularity, intervals, pagingSpec, filter, dimensions, metrics, context)

  def scan(
        dataSource: String,
        intervals: List[String],
        columns: List[String] = List.empty,
        batchSize: Option[Int] = Option.empty,
        filter: Option[Filter] = Option.empty,
        order: Option[String] = Option.empty,
        limit: Option[Int] = Option.empty,
        context: Option[Context] = Option.empty
      ): ScanQuery = ScanQuery("scan", dataSource, intervals, "list", batchSize, order, limit, filter, columns, context)

  def timeUnbounded(dataSource: String, context: Option[Context] = Option.empty): TimeBoundaryQuery =
    TimeBoundaryQuery("timeBoundary", dataSource, Option.empty, context = context)
  def timeUpperBounded(dataSource: String, context: Option[Context] = Option.empty): TimeBoundaryQuery =
    TimeBoundaryQuery("timeBoundary", dataSource, Some("maxTime"), context = context)
  def timeLowerBounded(dataSource: String, context: Option[Context] = Option.empty): TimeBoundaryQuery =
    TimeBoundaryQuery("timeBoundary", dataSource, Some("minTime"), context = context)
}

sealed trait Response

import scala.language.higherKinds
/**
  * This type class exists because :
  *   - Jackson cannot serialize/deserialize objects by Generic types that are type-erased at compile time
  *   - it binds corresponding Query and Response types together preventing user from passing incompatible combinations
  */
trait ResponseReader[Q <: Query, R <: Response, C[X]] {
  def read(response: String): C[R]
}

object ResponseReader {
  implicit object SelectResponseReader extends ResponseReader[SelectQuery, SelectResponse, Option] {
    def read(json: String): Option[SelectResponse] = ObjMapper.readValue[List[SelectResponse]](json).headOption
  }
  implicit object ScanResponseReader extends ResponseReader[ScanQuery, ScanResponse, Option] {
    def read(json: String): Option[ScanResponse] = ObjMapper.readValue[List[ScanResponse]](json).headOption
  }
  implicit object TimeSeriesResponseReader extends ResponseReader[TimeSeriesQuery, TimeSeriesResponse, Option] {
    def read(json: String): Option[TimeSeriesResponse] = ObjMapper.readValue[List[TimeSeriesResponse]](json).headOption
  }
  implicit object GroupByResponseReader extends ResponseReader[GroupByQuery, GroupByResponse, IndexedSeq] {
    def read(json: String): IndexedSeq[GroupByResponse] = ObjMapper.readValue[Vector[GroupByResponse]](json)
  }
  implicit object TopNQR extends ResponseReader[TopNQuery, TopNResponse, Option] {
    def read(json: String): Option[TopNResponse] = ObjMapper.readValue[List[TopNResponse]](json).headOption
  }
  implicit object SegmentMetadataResponseReader extends ResponseReader[SegmentMetadataQuery, SegmentMetadataResponse, Option] {
    def read(json: String): Option[SegmentMetadataResponse] = ObjMapper.readValue[List[SegmentMetadataResponse]](json).headOption
  }
  implicit object DataSourceMetadataResponseReader extends ResponseReader[DataSourceMetadataQuery, DataSourceMetadataResponse, Option] {
    def read(json: String): Option[DataSourceMetadataResponse] = ObjMapper.readValue[List[DataSourceMetadataResponse]](json).headOption
  }
}
