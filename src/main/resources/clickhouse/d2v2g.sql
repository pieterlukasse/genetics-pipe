create database if not exists ot;
create table if not exists ot.d2v2g_log(
  chr_id String,
  position UInt32,
  segment UInt32 MATERIALIZED (position % 1000000),
  ref_allele String,
  alt_allele String,
  variant_id String,
  rs_id String,
  stid String,
  index_variant_id String,
  r2 Nullable(Float64),
  afr_1000g_prop Nullable(Float64),
  mar_1000g_prop Nullable(Float64),
  eas_1000g_prop Nullable(Float64),
  eur_1000g_prop Nullable(Float64),
  sas_1000g_prop Nullable(Float64),
  log10_abf Nullable(Float64),
  posterior_prob Nullable(Float64),
  pmid Nullable(String),
  pub_date Nullable(String),
  pub_journal Nullable(String),
  pub_title Nullable(String),
  pub_author Nullable(String),
  trait_reported String,
  ancestry_initial Nullable(String),
  ancestry_replication Nullable(String),
  n_initial Nullable(UInt32),
  n_replication Nullable(UInt32),
  efo_code String,
  efo_label String,
  index_rs_id String,
  pval Nullable(Float64),
  index_chr_id String,
  index_position UInt32,
  index_ref_allele String,
  index_alt_allele String,
  gene_chr String,
  gene_id String,
  gene_start UInt32,
  gene_end UInt32,
  gene_name String,
  feature String,
  type_id String,
  source_id String,
  csq_counts Nullable(UInt32),
  qtl_beta Nullable(Float64),
  qtl_se Nullable(Float64),
  qtl_pval Nullable(Float64),
  interval_score Nullable(Float64)
)
engine = Log;

create table if not exists ot.d2v2g
engine MergeTree partition by (chr_id) order by (chr_id, position)
as select
  chr_id,
  position ,
  segment,
  ref_allele ,
  alt_allele ,
  variant_id ,
  rs_id ,
  stid ,
  index_variant_id ,
  r2 ,
  afr_1000g_prop ,
  mar_1000g_prop ,
  eas_1000g_prop ,
  eur_1000g_prop,
  sas_1000g_prop ,
  log10_abf ,
  posterior_prob ,
  pmid ,
  pub_date ,
  pub_journal ,
  pub_title ,
  pub_author ,
  trait_reported ,
  ancestry_initial ,
  ancestry_replication ,
  n_initial ,
  n_replication ,
  efo_code ,
  efo_label ,
  index_rs_id ,
  pval ,
  index_chr_id ,
  index_position ,
  index_ref_allele ,
  index_alt_allele ,
  gene_chr ,
  gene_id ,
  gene_start ,
  gene_end ,
  gene_name ,
  feature ,
  type_id ,
  source_id ,
  csq_counts ,
  qtl_beta ,
  qtl_se ,
  qtl_pval ,
  interval_score
from ot.d2v2g_log;