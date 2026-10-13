package com.zhku.knowledge.retrieval;
import com.zhku.knowledge.rulecheck.*;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
/** 验收固定ID与文件记录只留在test；资料准入共用受控运行装配，防止两份逻辑漂移。 */
final class Reviewed2025Trial extends Reviewed2025ControlledCatalogue {
    Reviewed2025Trial(Path root) throws Exception {super(root);}    RuleCheckApplicationService service(String selectedId) {
        EmbeddingClient disabled=new EmbeddingClient(){public int dimensions(){return 1;}public float[] embed(String q){throw new RetrieverUnavailableException("本批仅词法索引，不调用模型");}};
        return RuleCheckRuntimeFactory.create(dataSource,new RuleCheckRuntimeSettings(profile,CUT,SCOPE,REVISION,20,5),
                ()->List.copyOf(catalogue),(q,options)->selectedId,disabled,()->"b04-"+UUID.randomUUID(),Clock.systemUTC());
    }
    void save(String file,Object value) throws Exception {
        String directory=System.getProperty("zhku.rulecheck.evidenceDir");require(directory!=null,"证据目录必须显式提供");
        Path target=Path.of(directory).resolve(file);require(!Files.exists(target),"证据禁止覆盖");
        Files.write(target,mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(value));
    }
}
