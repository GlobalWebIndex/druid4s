package gwi.druid.client

import org.slf4j.{Logger, LoggerFactory}

import scala.sys.process.{Process, ProcessLogger}

trait DruidBootstrap {
  protected val logger: Logger = LoggerFactory.getLogger(getClass)

  private def compose(cmd: String) = Process(Array("/bin/sh", "-c", s"cd docker && docker-compose $cmd && cd .."))

  private def processLog = ProcessLogger(line => logger.info(line), line => logger.error(line))

  protected def startDruidContainer(): Unit = {
    stopDruidContainer()
    require(compose("up -d") ! processLog == 0)
  }

  protected def stopDruidContainer(): Unit =
    compose("down -v") ! processLog
}
