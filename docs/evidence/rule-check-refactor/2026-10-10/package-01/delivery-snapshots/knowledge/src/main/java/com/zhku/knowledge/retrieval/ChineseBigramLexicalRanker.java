package com.zhku.knowledge.retrieval;

import java.util.*;

/** 中文词法实验；仅排序已通过资料资格过滤的片段，不承担规则选题或事实判断。 */
final class ChineseBigramLexicalRanker {
    static List<RetrievedChunk> rank(String query,List<RetrievedChunk> eligible,int limit){
        if(limit<=0)return List.of();
        var ranked=rankWithoutDiversification(query,eligible,Integer.MAX_VALUE);
        var selected=new ArrayList<RetrievedChunk>();var seen=new HashSet<List<String>>();
        for(var chunk:ranked){
            // 仅折叠同来源同版本、正文完全相同的记录；前置记录编号不是政策数值。
            // 不用相似度阈值：一字否定或4/5学分差异都可能改变结论，不能当重复丢弃。
            String content=chunk.content().replaceFirst("^([^。！？\\n]{0,24}(?:样本|编号|条目|记录))\\d+(?=[，,。:：、])","$1");
            var key=Arrays.asList(Objects.toString(chunk.sourceId(),""),chunk.sourceVersion(),content);
            if(chunk.sourceId()==null||seen.add(key))selected.add(chunk);
            if(selected.size()==limit)break;
        }
        return List.copyOf(selected);
    }
    /** 同一合格语料的旧排序对照；保持旧评分和稳定tie-break，不按参考条款改写。 */
    static List<RetrievedChunk> rankWithoutDiversification(String query,List<RetrievedChunk> eligible,int limit){
        if(query==null||limit<=0)return List.of();
        Set<String> terms=new LinkedHashSet<>();
        for(String token:query.split("[^\\p{IsHan}A-Za-z0-9]+")){
            // 按码点分割，补充平面汉字不能被拆成半个代理对；汉字与拉丁串分别处理。
            int[] cps=token.codePoints().toArray();int start=0;
            while(start<cps.length){
                boolean han=Character.UnicodeScript.of(cps[start])==Character.UnicodeScript.HAN;int end=start+1;
                while(end<cps.length&&(Character.UnicodeScript.of(cps[end])==Character.UnicodeScript.HAN)==han)end++;
                if(han){for(int i=start;i+1<end;i++)terms.add(new String(cps,i,2));}
                else if(end-start>=2)terms.add(new String(cps,start,end-start));
                start=end;
            }
        }
        if(terms.isEmpty()||eligible.isEmpty())return List.of();
        var weights=new HashMap<String,Double>();
        // 必须先做资格过滤再统计df：内部/撤回资料既不能入候选，也不能影响可见文档排名。
        for(String term:terms){long df=eligible.stream().filter(c->c.content().contains(term)).count();weights.put(term,Math.log((eligible.size()+1.0)/(df+1.0))+1.0);}
        var scored=new ArrayList<Scored>();
        for(var chunk:eligible){double score=0;for(String term:terms)if(chunk.content().contains(term))score+=weights.get(term);
            if(score>0)scored.add(new Scored(chunk,score));}
        // 重建会分配新chunk UUID；不同正文同分时先用稳定正文/定位排序，避免前5仅因身份重建抖动。
        // 完全相同正文和定位才以UUID兜底，不借助题号、参考答案或条款偏好加分。
        scored.sort(Comparator.comparingDouble(Scored::score).reversed()
            .thenComparing(s->s.chunk().contentChecksum().toLowerCase(Locale.ROOT))
            .thenComparing(s->s.chunk().sourceLocator()).thenComparing(s->s.chunk().chunkId()));
        return scored.stream().limit(limit).map(Scored::chunk).toList();
    }
    private record Scored(RetrievedChunk chunk,double score){}
}
