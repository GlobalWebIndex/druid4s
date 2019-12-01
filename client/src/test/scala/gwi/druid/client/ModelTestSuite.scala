package gwi.druid.client

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class ModelTestSuite extends AnyFreeSpec with ScalaFutures with Matchers {

  val s = "string"
  val ss = List("string")

  def printJson(q: Query) = println {
    ObjMapper.prettyWriter.writeValueAsString(q)
  }

  "queries should serialize" in {
    List[Query](
      Query.timeSeries(
        s, s, ss, List(Aggregation.doubleMin(s,s)), Option(Filter.selector(s,s)), Option(PostAggregation.arithmetic(s,s, List(), Some(s))), Option.empty
      ),
      Query.groupBy(
        s, s, ss, List(Aggregation.cardinality(s,List(s), Some(true))), List(s), Option(GroupByLimitSpec.orderByAsc(s)), Option(HavingSpec.logicalAnd(List(s))), Option.empty, Option.empty, Option(Context(timeout = Option(1)))
      ),
      Query.topN(
        s, s, ss, List(Aggregation.longMax(s,s)), s, TopNMetric.inverted(s), 10, Option.empty, Option(PostAggregation.fieldAccess(s,s)), Option.empty
      ),
      Query.dataSourceMetadata(
        s, Option.empty
      ),
      Query.segmentMetadata(
        s, List(s), Option(ToInclude.list(List(s)))
      ),
      Query.timeLowerBounded(
        s, Option(Context(useCache = Option(true)))
      ),
      Query.timeUpperBounded(
        s, Option(Context(queryId = Option(s)))
      ),
      Query.timeUnbounded(
        s, Option.empty
      )
    ).map(ObjMapper.prettyWriter.writeValueAsString).foreach(println)
  }

  "indexing task should serialize" in {
    val indexTask =
      IndexTask(
        IndexTask.hadoopType,
        IngestionSpec(
          DataSchema(
            s,
            Parser.string(ParseSpec.json(TimestampSpec(s), DimensionsSpec(List(s), List.empty, List.empty))),
            List(Aggregation.longMax(s,s)),
            GranularitySpec.uniform(List(s))
          ),
          IoConfig.hadoop(InputSpec.granularity(s, s, s, Some(s)))
        )
      )
    println(ObjMapper.prettyWriter.writeValueAsString(indexTask))
  }
}
