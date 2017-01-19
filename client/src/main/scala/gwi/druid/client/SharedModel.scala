package gwi.druid.client

/** A.G.G.R.E.G.A.T.I.O.N **/

sealed trait Aggregation {
  def `type`: String
  def name: String
}
case class CountAggregator(`type`: String, name: String) extends Aggregation
case class NumericAggregator(`type`: String, name: String, fieldName: String) extends Aggregation
case class JavaScriptAggregator(`type`: String, name: String, fieldNames: List[String], fnAggregate: String, fnCombine: String, fnReset: String) extends Aggregation
case class CardinalityAggregator(`type`: String, name: String, fieldNames: List[String], byRow: Option[Boolean] = Option.empty) extends Aggregation
case class FilteredAggregator(`type`: String, filter: Filter, aggregator: Aggregation)
object Aggregation {
  def count(name: String): CountAggregator =
    CountAggregator("count", name)
  def longSum(name: String, fieldName: String): NumericAggregator =
    NumericAggregator("longSum", name, fieldName)
  def doubleSum(name: String, fieldName: String): NumericAggregator =
    NumericAggregator("doubleSum", name, fieldName)
  def doubleMin(name: String, fieldName: String): NumericAggregator =
    NumericAggregator("doubleMin", name, fieldName)
  def doubleMax(name: String, fieldName: String): NumericAggregator =
    NumericAggregator("doubleMax", name, fieldName)
  def longMin(name: String, fieldName: String): NumericAggregator =
    NumericAggregator("longMin", name, fieldName)
  def longMax(name: String, fieldName: String): NumericAggregator =
    NumericAggregator("longMax", name, fieldName)
  def hll(name: String, fieldName: String): NumericAggregator =
    NumericAggregator("hyperUnique", name, fieldName)
  def javaScript(name: String, fieldNames: List[String], fnAggregate: String, fnCombine: String, fnReset: String): JavaScriptAggregator =
    JavaScriptAggregator("javascript", name, fieldNames, fnAggregate, fnCombine, fnReset)
  def cardinality(name: String, fieldNames: List[String], byRow: Option[Boolean] = Option.empty): CardinalityAggregator =
    CardinalityAggregator("cardinality", name, fieldNames, byRow)
  def filtered(filter: Filter, aggregator: Aggregation): FilteredAggregator =
    FilteredAggregator("filtered", filter, aggregator)
}
