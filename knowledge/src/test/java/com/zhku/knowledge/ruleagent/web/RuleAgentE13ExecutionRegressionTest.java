package com.zhku.knowledge.ruleagent.web;

/** 同一套真实发送闸门用E13计划再跑一遍；只有回环合成供应商，不构造Live授权。 */
class RuleAgentE13ExecutionRegressionTest extends RuleAgentE02ExecutionRegressionTest {
    @Override String profileName() { return "E13"; }
}
