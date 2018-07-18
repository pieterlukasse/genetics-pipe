package ot.geckopipe.index

import com.typesafe.scalalogging.LazyLogging
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types._
import ot.geckopipe.Configuration
import ot.geckopipe.functions.{loadFromCSV, splitVariantID}

abstract class V2DIndex extends Indexable {
  def leanTable : DataFrame = selectBy(V2DIndex.indexColumns ++ V2DIndex.columns)
}

object V2DIndex extends LazyLogging  {
  val columns: Seq[String] = Seq("stid", "pmid", "index_chr_id", "index_position",
    "index_ref_allele", "index_alt_allele", "index_rs_id", "n_initial", "n_replication", "tag_variant_id",
    "r2", "afr_1000g_prop", "mar_1000g_prop", "eas_1000g_prop", "eur_1000g_prop",
    "sas_1000g_prop", "log10_abf", "posterior_prob")
  val indexColumns: Seq[String] = Seq("efo_code", "index_variant_id")

  val studiesSchema = StructType(
    StructField("stid", StringType) ::
      StructField("pmid", StringType) ::
      StructField("pub_date", StringType) ::
      StructField("pub_journal", StringType) ::
      StructField("pub_title", StringType) ::
      StructField("pub_author", StringType) ::
      StructField("trait_reported", StringType) ::
      StructField("trait_mapped", StringType) ::
      StructField("trait_efos", StringType) ::
      StructField("ancestry_initial", StringType) ::
      StructField("ancestry_replication", StringType) ::
      StructField("n_initial", LongType) ::
      StructField("n_replication", LongType) :: Nil)

  val topLociSchema = StructType(
    StructField("stid", StringType) ::
      StructField("variant_id", StringType) ::
      StructField("rs_id", StringType) ::
      StructField("pval_mantissa", DoubleType) ::
      StructField("pval_exponent", DoubleType) :: Nil)

  val ldSchema = StructType(
    StructField("stid", StringType) ::
      StructField("index_variant_id", StringType) ::
      StructField("tag_variant_id", StringType) ::
      StructField("r2", DoubleType) ::
      StructField("afr_1000g_prop", DoubleType) ::
      StructField("mar_1000g_prop", DoubleType) ::
      StructField("eas_1000g_prop", DoubleType) ::
      StructField("eur_1000g_prop", DoubleType) ::
      StructField("sas_1000g_prop", DoubleType) :: Nil)

  val finemappingSchema = StructType(
    StructField("stid", StringType) ::
      StructField("index_variant_id", StringType) ::
      StructField("tag_variant_id", StringType) ::
      StructField("log10_abf", DoubleType) ::
      StructField("posterior_prob", DoubleType) :: Nil)


  def build(vIdx: VariantIndex, conf: Configuration)(implicit ss: SparkSession): V2DIndex = {

    val studies = buildStudiesIndex(conf.variantDisease.studies)
    val topLoci = buildTopLociIndex(conf.variantDisease.toploci)
    val ldLoci = buildLDIndex(conf.variantDisease.ld)
    val fmLoci = buildFMIndex(conf.variantDisease.finemapping)

    val indexVariants = studies.join(topLoci, Seq("stid"))
    val ldExpansion = indexVariants.join(ldLoci, Seq("stid", "index_variant_id"))
      .select("stid", "index_variant_id", "tag_variant_id", "r2", "afr_1000g_prop",
        "mar_1000g_prop", "eas_1000g_prop", "eur_1000g_prop", "sas_1000g_prop")
    val fmExpansion = indexVariants
      .join(fmLoci, Seq("stid", "index_variant_id"))
      .select("stid", "index_variant_id", "tag_variant_id", "log10_abf", "posterior_prob")
    val ldAndFm = ldExpansion.join(fmExpansion,
      Seq("stid", "index_variant_id", "tag_variant_id"), "full_outer")
      .join(indexVariants, Seq("stid", "index_variant_id"), "left_outer")
      .drop("trait_mapped", "trait_efos", "trait_label", "trait_code")
      .withColumnRenamed("tag_variant_id", "variant_id")

    val ldAndFmEnriched = splitVariantID(ldAndFm).get
      .drop("variant_id")
      .join(vIdx.table, Seq("chr_id", "position", "ref_allele", "alt_allele"), "left_outer")

    new V2DIndex {
      override val table: DataFrame = ldAndFmEnriched
    }
  }

  def buildStudiesIndex(path: String)(implicit ss: SparkSession): DataFrame = {
    val processTraits = udf((codes: String, labels: String) => codes.split(";")
        .zipAll(labels.split(";"),"", "")
        .filter(_._1 != "")
        .map(t => Array(t._1,t._2)))

    val studies = loadFromCSV(path, studiesSchema)

    val pStudies = studies
      .withColumn("trait_label", when(col("trait_efos").isNull, col("trait_reported")).otherwise(col("trait_mapped")))
      .withColumn("trait_code", when(col("trait_mapped").isNull, col("trait_reported")).otherwise(col("trait_efos")))
      .withColumn("trait_pair",
        explode(processTraits(col("trait_code"), col("trait_label"))))
      .withColumn("efo_code", col("trait_pair").getItem(0))
      .withColumn("efo_label", col("trait_pair").getItem(1))
      .drop("trait_pair")

    pStudies
  }

  def buildTopLociIndex(path: String)(implicit ss: SparkSession): DataFrame = {
    val toDouble = udf((mantissa: Double, exponent: Double) => {
      val result = mantissa * Math.pow(10, exponent)
      result match {
        case Double.PositiveInfinity => Double.MaxValue
        case Double.NegativeInfinity => Double.MinValue
        case 0.0 => Double.MinPositiveValue
        case -0.0 => -Double.MinPositiveValue
        case _ => result
      }
    })

    val loci = loadFromCSV(path, topLociSchema)

    val fLoci = loci
      .withColumn("pval", toDouble(col("pval_mantissa"), col("pval_exponent")))
      .withColumn("index_variant_id", explode(split(col("variant_id"), ";")))

    splitVariantID(fLoci, "index_variant_id", "index_").get
      .withColumnRenamed("rs_id", "index_rs_id")
      .drop("pval_mantissa", "pval_exponent", "variant_id")
  }

  def buildLDIndex(path: String)(implicit ss: SparkSession): DataFrame = {
    val ld = loadFromCSV(path, ldSchema)

    ld
  }


  def buildFMIndex(path: String)(implicit ss: SparkSession): DataFrame = {
    val fm = loadFromCSV(path, finemappingSchema)

    fm
  }

  /** join built gtex and vep together and generate char pos alleles columns from variant_id */
  def load(conf: Configuration)(implicit ss: SparkSession): V2DIndex = {

    logger.info("load variant to gene dataset from built one")
    val v2d = ss.read
      .format("csv")
      .option("header", "true")
      .option("delimiter","\t")
      .load(conf.variantDisease.path)

    new V2DIndex {
      /** uniform way to get the dataframe */
      override val table: DataFrame = v2d
    }
  }
}