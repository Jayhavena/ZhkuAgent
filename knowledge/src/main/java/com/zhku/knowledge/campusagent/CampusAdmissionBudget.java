package com.zhku.knowledge.campusagent;
import java.util.*;
/** 仅管理准入预留；数据库/模型在锁外，执行Future的状态不能替代实际收尾。 */
public final class CampusAdmissionBudget {
    private final int perUser,instance;
    private final Map<UUID,Integer> users=new HashMap<>();
    private int total;
    public CampusAdmissionBudget(int perUser,int instance) {
        if(perUser<1||instance<perUser)throw new IllegalArgumentException("准入预算非法");
        this.perUser=perUser;this.instance=instance;
    }
    public static final class Failure extends RuntimeException {
        private final String code;
        Failure(String code){super(code,null,false,false);this.code=code;}
        public String code(){return code;}
    }
    public final class Reservation {
        private final UUID userId;
        private boolean released;
        private Reservation(UUID userId){this.userId=userId;}
        public void release(){synchronized(CampusAdmissionBudget.this){
            if(released)return;released=true;total--;
            users.compute(userId,(id,count)->count==1?null:count-1);
        }}
    }
    public synchronized Reservation reserve(UUID userId){
        Objects.requireNonNull(userId);
        if(users.getOrDefault(userId,0)>=perUser)throw new Failure("USER_IN_FLIGHT_LIMIT");
        if(total>=instance)throw new Failure("INSTANCE_CAPACITY_EXCEEDED");
        total++;users.merge(userId,1,Integer::sum);return new Reservation(userId);
    }
    public synchronized int inFlight(){return total;}
    public synchronized int inFlight(UUID userId){return users.getOrDefault(userId,0);}
}
