package com.zhku.knowledge.ruleagent.web;

import com.zhku.knowledge.ruleagent.RuleAgentContract.Owner;
import com.zhku.knowledge.ruleagent.RuleAgentContract;
import com.zhku.knowledge.ruleagent.RuleAgentEvidenceTools;
import com.zhku.knowledge.rulecheck.RuleCheckVerifiedExcerpt;
import java.time.*;
import java.util.*;

/** 只连接工厂与HTTP原文端口，不管理Agent执行、发布或任务状态。 */
public final class RuleAgentTaskToolsRegistry implements RuleAgentTaskController.OriginalReader,AutoCloseable {
    @FunctionalInterface public interface ToolFactory {RuleAgentEvidenceTools create(Owner owner,UUID taskId);}
    private record Key(Owner owner,UUID taskId){}
    private static final class Entry {
        final Instant expires;
        RuleAgentEvidenceTools tools;
        Entry(Instant expires){this.expires=expires;}
    }
    private final Object lock=new Object();
    private final Map<Key,Entry> entries=new HashMap<>();
    private final ToolFactory factory;
    private final Clock clock;
    private final int capacity;
    private boolean closed;
    public RuleAgentTaskToolsRegistry(ToolFactory factory,Clock clock,int capacity){
        this.factory=Objects.requireNonNull(factory);this.clock=Objects.requireNonNull(clock);
        if(capacity<1||capacity>2048)throw new IllegalArgumentException("任务原文索引容量无效");this.capacity=capacity;
    }
    public RuleAgentEvidenceTools open(Owner owner,UUID taskId){
        var key=new Key(Objects.requireNonNull(owner),Objects.requireNonNull(taskId));Entry entry;
        synchronized(lock){
            ensureOpen();prune();var old=entries.get(key);
            if(old!=null){if(old.tools==null)throw new IllegalStateException("任务工具正在初始化");return old.tools;}
            if(entries.size()>=capacity)throw new IllegalStateException("任务原文索引已满");
            entry=new Entry(clock.instant().plusMillis(RuleAgentContract.DEFAULT_BUDGET.taskTtlMillis()));entries.put(key,entry);
        }
        try{
            // 工具初始化可能涉及受控资料读取；不持索引锁执行，避免阻塞其他任务的原文拒绝/退出。
            var tools=Objects.requireNonNull(factory.create(owner,taskId));
            synchronized(lock){
                if(closed||entries.get(key)!=entry||!clock.instant().isBefore(entry.expires))throw new IllegalStateException("任务工具初始化已失效");
                entry.tools=tools;return tools;
            }
        }catch(RuntimeException|Error failure){synchronized(lock){entries.remove(key,entry);}throw failure;}
    }
    @Override public Optional<RuleCheckVerifiedExcerpt> read(Owner owner,UUID taskId,UUID token){
        if(owner==null||taskId==null||token==null)return Optional.empty();var key=new Key(owner,taskId);Entry entry;
        synchronized(lock){if(closed)return Optional.empty();prune();entry=entries.get(key);if(entry==null||entry.tools==null)return Optional.empty();}
        // 索引命中不是授权：目录、资格、正文hash和引用跨度仍由冻结的tools.original每次重新检查。
        var quote=entry.tools.original(owner,taskId,token);
        synchronized(lock){return !closed&&entries.get(key)==entry&&clock.instant().isBefore(entry.expires)?quote:Optional.empty();}
    }
    public int size(){synchronized(lock){prune();return entries.size();}}
    private void prune(){Instant now=clock.instant();entries.entrySet().removeIf(e->!now.isBefore(e.getValue().expires));}
    private void ensureOpen(){if(closed)throw new IllegalStateException("任务原文索引已关闭");}
    @Override public void close(){synchronized(lock){closed=true;entries.clear();}}
}
