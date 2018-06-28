package ot.geckopipe.qtl

import com.typesafe.scalalogging.LazyLogging
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types._
import org.apache.spark.sql.{DataFrame, SparkSession}
import ot.geckopipe.Configuration
import ot.geckopipe.index.VariantIndex
import ot.geckopipe.functions._

object QTL extends LazyLogging {
  val schema = StructType(
    StructField("chr_id", StringType) ::
      StructField("position", LongType) ::
      StructField("ref_allele", StringType) ::
      StructField("alt_allele", StringType) ::
      StructField("gene_id", StringType) ::
      StructField("beta", DoubleType) ::
      StructField("se", DoubleType) ::
      StructField("pval", DoubleType) :: Nil)

  def load(from: String)(implicit ss: SparkSession): DataFrame = {
    val qtl = ss.read
      .format("csv")
      .option("header", "true")
      .option("inferSchema", "false")
      .option("delimiter","\t")
      .option("mode", "DROPMALFORMED")
      .schema(schema)
      .load(from)
      .withColumn("filename", input_file_name)

    qtl
  }

  /** union all intervals and interpolate variants from intervals */
  def apply(vIdx: VariantIndex, conf: Configuration)(implicit ss: SparkSession): DataFrame = {
    val extractValidTokensFromPathUDF = udf((path: String) => extractValidTokensFromPath(path, "qtl"))

    logger.info("generate pchic dataset from file and aggregating by range and gene")
    val qtls = load(conf.qtl.path)
      .withColumn("tokens", extractValidTokensFromPathUDF(col("filename")))
      .withColumn("source_id", lower(col("tokens").getItem(0)))
      .withColumn("feature", lower(col("tokens").getItem(1)))
      .withColumn("value", array(col("beta"), col("se"), col("pval")))
      .drop("filename", "tokens", "beta", "se", "pval")
      .repartitionByRange(col("chr_id").asc, col("position").asc)

    qtls.join(vIdx.table, Seq("chr_id", "position", "ref_allele", "alt_allele"))
  }
}