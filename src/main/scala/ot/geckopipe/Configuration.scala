package ot.geckopipe

/**
  * Case class to map to a gtex section in the configuration file.
  *
  * @param sampleFactor enabled if > .0 by default .0
  * @param tissueMap a file to the tsv file containing "tissuename\tcode"
  * @param egenes a file pattern to the tsv file containing eGenes wildcarding all tissues
  * @param variantGenePairs a file pattern to the tsv file containing significative variant gene pairs
  */
case class GTExSection(sampleFactor: Double, tissueMap: String, egenes: String, variantGenePairs: String)

/**
  * Main configuration case class
  *
  * @param sparkUri the uri to connect to spark empty by default
  * @param gtex the GTExSection main section
  */
case class Configuration(sparkUri: String, logLevel: String, gtex: GTExSection)

object Configuration {
  // companion object but nothing at the moment
}