package com.zhku.knowledge.rulecheck;

/** 当前资格SQL同一次读取的正文/元数据；记录页码不自动证明物理或印刷页码语义。 */
public record RuleCheckLocatedEvidence(RuleCheckEvidenceSnapshot snapshot,String title,String sourceUrl,
                                      String sourceLocator,Integer recordedPageNumber,String sectionTitle) { }
