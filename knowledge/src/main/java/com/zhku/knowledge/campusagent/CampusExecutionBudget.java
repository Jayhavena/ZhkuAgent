package com.zhku.knowledge.campusagent;
import java.time.Duration;
import java.util.function.*;
import java.util.Objects;
/** 每Run独享；外发前占用次数，未知响应不退款，停止期限包含排队而不等于远端资源退出。 */
public final class CampusExecutionBudget {
    private final long began,totalNanos;
    private final LongSupplier clock;
    private final BooleanSupplier stopped;
    private int attempts;
    public CampusExecutionBudget(Duration duration) {this(System.nanoTime(),duration,System::nanoTime,()->false);}
    CampusExecutionBudget(long began,Duration duration,LongSupplier clock,BooleanSupplier stopped) {
        Objects.requireNonNull(duration);
        if(duration.isNegative()||duration.isZero()||duration.compareTo(Duration.ofSeconds(30))>0)
            throw new IllegalArgumentException("任务预算须在0至30秒内");
        this.began=began;this.totalNanos=duration.toNanos();this.clock=Objects.requireNonNull(clock);this.stopped=Objects.requireNonNull(stopped);
    }
    public static final class Failure extends RuntimeException {
        private final String code;
        Failure(String code){super(code,null,false,false);this.code=code;}
        public String code(){return code;}
    }
    public synchronized void reserveModelCall(String prompt){
        checkDeadline();
        if(prompt==null||prompt.length()>12000)throw new Failure("MODEL_INPUT_LIMIT");
        if(attempts>=3)throw new Failure("MODEL_CALL_BUDGET_EXCEEDED");
        attempts++;
    }
    public void checkDeadline(){
        // 已记录的用户取消优先；新总期限到达由宿主/Service转为真实RUN_TIMEOUT。
        if(stopped.getAsBoolean())throw new Failure("CANCELLED");
        if(clock.getAsLong()-began>=totalNanos)throw new Failure("RUN_TIMEOUT");
    }
    public synchronized int attemptedCalls(){return attempts;}
}
