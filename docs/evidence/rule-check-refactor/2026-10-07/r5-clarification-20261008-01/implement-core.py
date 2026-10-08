from pathlib import Path
root=Path(__file__).resolve().parents[5]
p=root/'knowledge/src/main/java/com/zhku/knowledge/rulecheck/RuleCheckApplicationService.java'
s=p.read_text(encoding='utf-8');a=s.index('    public RuleCheckDecision check(');b=s.index('    private Map<String,CatalogEntry> approvedCatalogue()')
body=s[a:s.index('    public RuleCheckScopedResult checkWithContext(',a)]
body=body.replace('public RuleCheckDecision check(String question, Map<String,String> confirmedConditions)',
 'private RuleCheckScopedResult execute(String question, Map<String,String> values, RuleCheckScopedResult.Continuation expected)')
body=body.replace('        var values=validatedConditions(question,confirmedConditions);\n','').replace('return refused(','return rejected(')
body=body.replace('String id=selector.select(question,options);','''String id;
            if (expected==null) id=selector.select(question,options);
            else {
                id=expected.entry().template().ruleId();
                if (!expected.entry().equals(approved.get(id))) return rejected("澄清所用规则已变化或退役，请重新查证");
            }''')
body=body.replace('return decisions.decide(entry.template(),values,captured.snapshots());',
 'return scoped(decisions.decide(entry.template(),values,captured.snapshots()),entry,question,values);')
wrappers='''    public RuleCheckDecision check(String question, Map<String,String> confirmedConditions) {
        return checkWithContext(question,confirmedConditions).decision();
    }
    public RuleCheckScopedResult checkWithContext(String question,Map<String,String> confirmedConditions) {
        return execute(question,validatedConditions(question,confirmedConditions),null);
    }
    public RuleCheckScopedResult resume(RuleCheckScopedResult.Continuation context,Map<String,String> delta) {
        if (context==null) throw new IllegalArgumentException("澄清上下文缺失");
        if (!settings.equals(context.settings()) || !context.entry().template().conditions().contains(context.nextCondition()))
            return rejected("澄清运行边界已变化，请重新查证");
        String key=context.nextCondition().key();
        if (delta==null || delta.size()!=1 || !delta.containsKey(key)
                || !context.nextCondition().allowedValues().contains(delta.get(key)))
            throw new IllegalArgumentException("只允许补充本轮待问条件的声明值");
        String previous=context.confirmedConditions().get(key);
        if (previous!=null && !previous.isBlank()) throw new IllegalArgumentException("更改已声明条件须重新查证");
        var values=new LinkedHashMap<>(context.confirmedConditions());values.put(key,delta.get(key));
        // 续答不重新选题，也不能沿用旧证据；配置/目录复核后仍走完整捕获与当前核验。
        return execute(context.question(),validatedConditions(context.question(),values),context);
    }
'''
helpers='''    private RuleCheckScopedResult scoped(RuleCheckDecision decision,CatalogEntry entry,String question,Map<String,String> values) {
        if (decision.status()==RuleCheckDecision.Status.INSUFFICIENT_EVIDENCE)
            return new RuleCheckScopedResult(decision,null,null);
        var rule=entry.template();var when=new LinkedHashMap<String,Map<String,String>>();
        rule.facts().forEach(fact->when.put(fact.id(),fact.when()));
        var scope=new RuleCheckScopedResult.Scope(rule.ruleId(),entry.questionScope(),rule.conditions(),when);
        RuleCheckScopedResult.Continuation continuation=null;
        if (decision.status()==RuleCheckDecision.Status.NEED_CLARIFICATION) {
            var questionItem=decision.clarificationQuestions().getFirst();
            var condition=rule.conditions().stream().filter(c->c.key().equals(questionItem.key())).findFirst().orElseThrow();
            // 接口有输入上限；不能发出用户按合同永远无法回答的条件选项。
            if (condition.key().length()>64 || condition.allowedValues().stream().anyMatch(v->v.length()>128))
                return rejected("本规则的澄清字段超出支持范围");
            continuation=new RuleCheckScopedResult.Continuation(settings,entry,question,values,condition);
        }
        return new RuleCheckScopedResult(decision,scope,continuation);
    }
    private RuleCheckScopedResult rejected(String reason) {return new RuleCheckScopedResult(refused(reason),null,null);}
'''
p.write_text(s[:a]+wrappers+body+helpers+s[b:],encoding='utf-8')
print('Application continuation: fixed rule, fresh evidence each round')
