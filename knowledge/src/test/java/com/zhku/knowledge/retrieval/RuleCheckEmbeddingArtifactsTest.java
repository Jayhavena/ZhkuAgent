package com.zhku.knowledge.retrieval;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class RuleCheckEmbeddingArtifactsTest {
    @TempDir Path directory;
    final ObjectMapper mapper=new ObjectMapper();
    final Map<String,String> inputs=new LinkedHashMap<>();
    final List<Map<String,Object>> entries=new ArrayList<>();
    Map<String,Object> manifest;
    Path prepare() throws Exception {
        for(int i=1;i<=30;i++){
            String id="ID"+i,text="合成合同文本"+i;inputs.put(id,text);
            var row=new LinkedHashMap<String,Object>();row.put("id",id);row.put("text",text);row.put("inputSha256",Reviewed2025Trial.sha(text.getBytes(StandardCharsets.UTF_8)));
            row.put("status","SUCCESS");row.put("actualHttpAttempt",true);row.put("usageKnown",true);row.put("promptTokens",1);row.put("totalTokens",1);row.put("vector",Collections.nCopies(1024,0.1));
            Path file=directory.resolve(id+".json");Files.write(file,mapper.writeValueAsBytes(row));entries.add(new LinkedHashMap<>(Map.of("id",id,"path",file.getFileName().toString(),"sha256",Reviewed2025Trial.sha(Files.readAllBytes(file)),"status","SUCCESS")));
        }
        manifest=new LinkedHashMap<>();manifest.put("kind","ACTUAL_PROVIDER_EMBEDDINGS_FOR_CONTROLLED_RANKING");manifest.put("complete",true);manifest.put("model","text-embedding-v4");manifest.put("dimensions",1024);
        manifest.put("actualHttpAttempts",30);manifest.put("successes",30);manifest.put("failedUsageUnknown",0);manifest.put("observedTotalTokens",30);manifest.put("retryCount",0);manifest.put("reportQueriesConsumed",0);manifest.put("files",entries);
        return saveManifest();
    }
    Path saveManifest() throws Exception {Path file=directory.resolve("manifest.json");Files.write(file,mapper.writeValueAsBytes(manifest));return file;}
    RuleCheckEmbeddingArtifacts load(Path file) throws Exception {return RuleCheckEmbeddingArtifacts.load(file,Reviewed2025Trial.sha(Files.readAllBytes(file)),inputs);}
    void mutateRecord(String key,Object value) throws Exception {
        Path file=directory.resolve("ID1.json");var row=mapper.readValue(Files.readAllBytes(file),LinkedHashMap.class);row.put(key,value);Files.write(file,mapper.writeValueAsBytes(row));entries.get(0).put("sha256",Reviewed2025Trial.sha(Files.readAllBytes(file)));saveManifest();
    }
    @Test void validArtifactReturnsOnlyVectorsForExactOriginalInput() throws Exception {
        var loaded=load(prepare());assertThat(loaded.vector("ID1",inputs.get("ID1"))).hasSize(1024);
        assertThatThrownBy(()->loaded.vector("ID1","改写问题")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->loaded.vector("OTHER","text")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void fingerprintAndFileTamperingFailBeforeDatabase() throws Exception {
        Path file=prepare();assertThatThrownBy(()->RuleCheckEmbeddingArtifacts.load(file,"0".repeat(64),inputs)).isInstanceOf(IllegalArgumentException.class);
        Files.writeString(directory.resolve("ID1.json"),"{}");assertThatThrownBy(()->load(file)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void incompleteHealthOrWrongModelIsRejected() throws Exception {
        Path file=prepare();manifest.put("complete",false);saveManifest();assertThatThrownBy(()->load(file)).isInstanceOf(IllegalArgumentException.class);
        manifest.put("complete",true);manifest.put("model","other");saveManifest();assertThatThrownBy(()->load(file)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void modifiedInputAndWrongDimensionAreRejected() throws Exception {
        Path file=prepare();mutateRecord("text","其他正文");assertThatThrownBy(()->load(file)).isInstanceOf(IllegalArgumentException.class);
        mutateRecord("text",inputs.get("ID1"));mutateRecord("vector",List.of(0.1));assertThatThrownBy(()->load(file)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void unknownUsageOrNonProviderRecordIsRejected() throws Exception {
        Path file=prepare();mutateRecord("usageKnown",false);assertThatThrownBy(()->load(file)).isInstanceOf(IllegalArgumentException.class);
        mutateRecord("usageKnown",true);mutateRecord("actualHttpAttempt",false);assertThatThrownBy(()->load(file)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void duplicateAndPathEscapeAreRejected() throws Exception {
        Path file=prepare();entries.get(1).put("id","ID1");saveManifest();assertThatThrownBy(()->load(file)).isInstanceOf(IllegalArgumentException.class);
        entries.get(1).put("id","ID2");entries.get(0).put("path","../escape.json");saveManifest();assertThatThrownBy(()->load(file)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void singleRouteOutageCannotMasqueradeAsQuality() {
        assertThatThrownBy(()->RuleCheck2025VectorCalibrationTest.requireHealth("HYBRID",new RetrievalOutcome(List.of(),true,false))).isInstanceOf(AssertionError.class);
        assertThatThrownBy(()->RuleCheck2025VectorCalibrationTest.requireHealth("VECTOR",new RetrievalOutcome(List.of(),false,false))).isInstanceOf(AssertionError.class);
    }
}
