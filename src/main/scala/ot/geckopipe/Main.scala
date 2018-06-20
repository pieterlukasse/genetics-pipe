package ot.geckopipe

import java.nio.file.Paths

import com.typesafe.scalalogging.LazyLogging
import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession
import ot.geckopipe.index.{V2GIndex, VariantIndex}
import ot.geckopipe.interval.Interval
import ot.geckopipe.positional.Positional
import scopt.OptionParser

sealed trait Command
case class VICmd() extends Command
case class V2GCmd() extends Command
case class V2GStatsCmd() extends Command

case class CommandLineArgs(file: String = "", kwargs: Map[String,String] = Map(), command: Option[Command] = None)

object Main extends LazyLogging {
  val progVersion: String = "0.12"
  val progName: String = "gecko-pipe"
  val entryText: String =
    """
      |
      |NOTE:
      |copy logback.xml locally, modify it with desired logger levels and specify
      |-Dlogback.configurationFile=/path/to/customised/logback.xml. Keep in mind
      |that "Logback-classic can scan for changes in its configuration file and
      |automatically reconfigure itself when the configuration file changes".
      |So, you even don't need to relaunch your process to change logging levels
      | -- https://goo.gl/HMXCqY
      |
    """.stripMargin

  def run(config: CommandLineArgs): Unit = {
    println(s"running $progName version $progVersion")
    val conf = if (config.file.nonEmpty) {
        logger.info(s"loading configuration from commandline as ${config.file}")
        pureconfig.loadConfig[Configuration](Paths.get(config.file))
      } else {
        logger.info("load configuration from package resource")
        pureconfig.loadConfig[Configuration]
      }

    conf match {
      case Right(c) =>
        logger.debug(s"running with cli args $config and with default configuracion $c")
        val logLevel = c.logLevel

        val conf: SparkConf =
          if (c.sparkUri.nonEmpty)
            new SparkConf()
              .setAppName(progName)
              .setMaster(s"${c.sparkUri}")
          else
            new SparkConf()
              .setAppName(progName)


        implicit val ss: SparkSession = SparkSession.builder
          .config(conf)
          .getOrCreate

        logger.debug("setting sparkcontext logging level to log-level")
        ss.sparkContext.setLogLevel(logLevel)

        // needed for save dataset function
        implicit val sampleFactor: Double = c.sampleFactor

        logger.info("check command specified")
        config.command match {
          case Some(cmd: VICmd) =>
            logger.info("exec variant-index command")

            val _ = VariantIndex.builder(c).build

          case Some(cmd: V2GCmd) =>
            logger.info("exec variant-gene command")

            val vIdx = VariantIndex.builder(c).load
            val positionalDts = Positional.buildPositionals(vIdx, c)
            val intervalDts = Interval.buildIntervals(vIdx, c)

            val dtSeq = positionalDts ++ intervalDts
            val v2g = V2GIndex.build(dtSeq, vIdx, c)

            v2g.save(c.output.stripSuffix("/").concat("/v2g/"))

          case Some(cmd: V2GStatsCmd) =>
            logger.info("exec variant-gene-stats command")

            val vIdx = VariantIndex.builder(c).load
            val positionalDts = Positional.buildPositionals(vIdx, c)
            val intervalDts = Interval.buildIntervals(vIdx, c)

            val dtSeq = positionalDts ++ intervalDts
            val v2g = V2GIndex.load(c)

            val stats = v2g.computeStats
            logger.info(s"computed stats $stats")

          case None =>
            logger.error("failed to specify a command to run try --help")
        }

        ss.stop

      case Left(failures) => println(s"configuration contains errors like ${failures.toString}")
    }
    println("closing app... done.")
  }

  def main(args: Array[String]): Unit = {
    // parser.parse returns Option[C]
    parser.parse(args, CommandLineArgs()) match {
      case Some(config) =>
        run(config)
      case None => println("problem parsing commandline args")
    }
  }

  val parser:OptionParser[CommandLineArgs] = new OptionParser[CommandLineArgs](progName) {
    head(progName, progVersion)

    opt[String]("file")
      .abbr("f")
      .valueName("<config-file>")
      .action( (x, c) => c.copy(file = x) )
      .text("file contains the configuration needed to run the pipeline")

    opt[Map[String,String]]("kwargs")
      .valueName("k1=v1,k2=v2...")
      .action( (x, c) => c.copy(kwargs = x) )
      .text("other arguments")

    cmd("variant-index")
      .action( (_, c) => c.copy(command = Some(VICmd())) )
      .text("generate variant index from VEP file")

    cmd("variant-gene").
      action( (_, c) => c.copy(command = Some(V2GCmd())))
      .text("generate variant to gene table")


    cmd("variant-gene-stats").
      action( (_, c) => c.copy(command = Some(V2GStatsCmd())))
      .text("generate variant to gene stat results")

    note(entryText)

    override def showUsageOnError = true
  }
}
