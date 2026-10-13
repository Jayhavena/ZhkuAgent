package com.zhku.knowledge.retrieval;

import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/** 已实际外发的不可变向量产物读取；本类绝不访问模型网络。 */
final class RuleCheckEmbeddingArtifacts {
    private final Map<String,String> inputs;
    private final Map<String,float[]> vectors;
    private RuleCheckEmbeddingArtifacts(Map<String,String> inputs,Map<String,float[]> vectors){this.inputs=Map.copyOf(inputs);this.vectors=Map.copyOf(vectors);}
    static RuleCheckEmbeddingArtifacts load(Path manifest,String expectedSha,Map<String,String> inputs) throws Exception {
        var mapper=new ObjectMapper();byte[] bytes=Files.readAllBytes(manifest);
        require(expectedSha.matches("[0-9a-f]{64}")&&Reviewed2025Trial.sha(bytes).equals(expectedSha));
        var m=mapper.readTree(bytes);
        require(m.path("kind").asText().equals("ACTUAL_PROVIDER_EMBEDDINGS_FOR_CONTROLLED_RANKING")&&m.path("complete").asBoolean()
            &&m.path("model").asText().equals("text-embedding-v4")&&m.path("dimensions").asInt()==1024
            &&m.path("actualHttpAttempts").asInt()==30&&m.path("successes").asInt()==30&&m.path("failedUsageUnknown").asInt(-1)==0
            &&m.path("retryCount").asInt(-1)==0&&m.path("reportQueriesConsumed").asInt(-1)==0&&m.path("files").size()==30&&inputs.size()==30);
        var vectors=new HashMap<String,float[]>();var paths=new HashSet<Path>();long tokens=0;
        Path base=manifest.toAbsolutePath().normalize().getParent();
        for(var entry:m.path("files")){
            String id=entry.path("id").asText(),name=entry.path("path").asText();
            require(inputs.containsKey(id)&&!vectors.containsKey(id)&&name.matches("[A-Za-z0-9]+\\.json")&&entry.path("status").asText().equals("SUCCESS"));
            Path path=base.resolve(name).normalize();require(path.startsWith(base)&&paths.add(path));byte[] rowBytes=Files.readAllBytes(path);
            require(Reviewed2025Trial.sha(rowBytes).equals(entry.path("sha256").asText()));var row=mapper.readTree(rowBytes);
            require(row.path("id").asText().equals(id)&&row.path("status").asText().equals("SUCCESS")&&row.path("actualHttpAttempt").asBoolean()&&row.path("usageKnown").asBoolean()
                &&row.path("text").asText().equals(inputs.get(id))&&row.path("inputSha256").asText().equals(Reviewed2025Trial.sha(inputs.get(id).getBytes(StandardCharsets.UTF_8))));
            require(row.path("promptTokens").isIntegralNumber()&&row.path("totalTokens").isIntegralNumber()&&row.path("promptTokens").asLong()>=0&&row.path("totalTokens").asLong()>=row.path("promptTokens").asLong());
            var array=row.path("vector");require(array.isArray()&&array.size()==1024);float[] vector=new float[1024];double norm=0;
            for(int i=0;i<vector.length;i++){require(array.get(i).isNumber());double value=array.get(i).asDouble();require(Double.isFinite(value)&&Math.abs(value)<1e30);vector[i]=(float)value;norm+=vector[i]*vector[i];}
            require(norm>0&&Double.isFinite(norm));vectors.put(id,vector);tokens+=row.path("totalTokens").asLong();
        }
        require(vectors.keySet().equals(inputs.keySet())&&tokens==m.path("observedTotalTokens").asLong(-1));
        return new RuleCheckEmbeddingArtifacts(inputs,vectors);
    }
    float[] vector(String id,String text){require(text!=null&&text.equals(inputs.get(id)));return vectors.get(id).clone();}
    private static void require(boolean okay){if(!okay)throw new IllegalArgumentException("实际向量产物/原输入合同不符");}
}
