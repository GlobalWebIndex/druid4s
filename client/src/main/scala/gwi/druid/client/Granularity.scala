package gwi.druid.client

import org.joda.time.{Days, _}

sealed protected[druid] trait Granularity {
  import Granularity._
  def getUnits(n: Int): ReadablePeriod
  def truncate(time: DateTime): DateTime
  def numIn(interval: ReadableInterval): Int
  def arity: Int

  def increment(time: DateTime): DateTime = time.plus(getUnits(1))
  def increment(time: DateTime, count: Int): DateTime = time.plus(getUnits(count))
  def decrement(time: DateTime): DateTime = time.minus(getUnits(1))
  def decrement(time: DateTime, count: Int): DateTime = time.minus(getUnits(count))
  def getIterable(start: DateTime, end: DateTime): Iterable[Interval] = getIterable(new Interval(start, end))
  def getIterable(input: Interval): Iterable[Interval] = new IntervalIterable(input)
  def getReverseIterable(start: DateTime, end: DateTime): Iterable[Interval] = getReverseIterable(new Interval(start, end))
  def getReverseIterable(input: Interval): Iterable[Interval] = new ReverseIntervalIterable(input)

  def bucket(t: DateTime): Interval = {
    val start = truncate(t)
    new Interval(start, increment(start))
  }

  def widen(interval: Interval): Interval = {
    val intervalStart = truncate(interval.getStart)
    val intervalEnd =
      interval.getEnd match {
        case end if end == intervalStart => increment(intervalStart)
        case end if truncate(end) == end => end
        case end => increment(truncate(end))
      }
    new Interval(intervalStart, intervalEnd)
  }

  override def toString: String = this match {
      case MONTH  => "MONTH"
      case WEEK   => "WEEK"
      case DAY    => "DAY"
      case HOUR   => "HOUR"
      case MINUTE => "MINUTE"
      case SECOND => "SECOND"
    }

  class IntervalIterable(inputInterval: Interval) extends Iterable[Interval] {
    def iterator = new Iterator[Interval] {
      private var currStart = truncate(inputInterval.getStart)
      private var currEnd = increment(currStart)

      def hasNext = currStart.isBefore(inputInterval.getEnd)

      def next: Interval = {
        if (!hasNext) {
          throw new NoSuchElementException("There are no more intervals")
        }
        val retVal = new Interval(currStart, currEnd)
        currStart = currEnd
        currEnd = increment(currStart)
        retVal
      }
    }
  }

  class ReverseIntervalIterable(inputInterval: Interval) extends Iterable[Interval] {
    def iterator = new Iterator[Interval] {
      private var currEnd = inputInterval.getEnd
      private var currStart = decrement(currEnd)

      def hasNext: Boolean = currEnd.isAfter(inputInterval.getStart)

      def next = {
        if (!hasNext) {
          throw new NoSuchElementException("There are no more intervals")
        }
        val retVal = new Interval(currStart, currEnd)
        currEnd = currStart
        currStart = decrement(currEnd)
        retVal
      }
    }
  }

}

protected[druid] object Granularity {

  def apply(value: String): Granularity = value match {
    case "MONTH" => MONTH
    case "WEEK" => WEEK
    case "DAY" => DAY
    case "HOUR" => HOUR
    case "MINUTE" => MINUTE
    case "SECOND" => SECOND
  }

  object MONTH extends Granularity {
    def arity = 2
    def getUnits(n: Int) = Months.months(n)
    def numIn(interval: ReadableInterval) = Months.monthsIn(interval).getMonths
    def truncate(time: DateTime) = {
      val mutableDateTime = time.toMutableDateTime
      mutableDateTime.setMillisOfDay(0)
      mutableDateTime.setDayOfMonth(1)
      mutableDateTime.toDateTime
    }
  }

  object WEEK extends Granularity {
    def arity = DAY.arity
    def getUnits(n: Int) = Weeks.weeks(n)
    def numIn(interval: ReadableInterval) = Weeks.weeksIn(interval).getWeeks
    def truncate(time: DateTime) = {
      val mutableDateTime = time.toMutableDateTime
      mutableDateTime.setMillisOfDay(0)
      mutableDateTime.setDayOfWeek(1)
      mutableDateTime.toDateTime
    }
  }

  object DAY extends Granularity {
    def arity = 3
    def getUnits(n: Int) = Days.days(n)
    def numIn(interval: ReadableInterval): Int = Days.daysIn(interval).getDays
    def truncate(time: DateTime): DateTime = {
      val mutableDateTime = time.toMutableDateTime
      mutableDateTime.setMillisOfDay(0)
      mutableDateTime.toDateTime
    }
  }

  object HOUR extends Granularity {
    def arity = 4
    def getUnits(n: Int) = Hours.hours(n)
    def numIn(interval: ReadableInterval) = Hours.hoursIn(interval).getHours
    def truncate(time: DateTime): DateTime = {
      val mutableDateTime = time.toMutableDateTime
      mutableDateTime.setMillisOfSecond(0)
      mutableDateTime.setSecondOfMinute(0)
      mutableDateTime.setMinuteOfHour(0)
      mutableDateTime.toDateTime
    }
  }

  object MINUTE extends Granularity {
    def arity = 5
    def getUnits(count: Int) = Minutes.minutes(count)
    def numIn(interval: ReadableInterval) = Minutes.minutesIn(interval).getMinutes
    def truncate(time: DateTime) = {
      val mutableDateTime = time.toMutableDateTime
      mutableDateTime.setMillisOfSecond(0)
      mutableDateTime.setSecondOfMinute(0)
      mutableDateTime.toDateTime
    }
  }

  object SECOND extends Granularity {
    def arity = 6
    def getUnits(count: Int) = Seconds.seconds(count)
    def numIn(interval: ReadableInterval) = Seconds.secondsIn(interval).getSeconds
    def truncate(time: DateTime) = {
      val mutableDateTime = time.toMutableDateTime
      mutableDateTime.setMillisOfSecond(0)
      mutableDateTime.toDateTime
    }
  }

}
