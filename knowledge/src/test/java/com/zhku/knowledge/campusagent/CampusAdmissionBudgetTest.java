package com.zhku.knowledge.campusagent;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CampusAdmissionBudgetTest {
    @Test void userTwoAndInstanceEighteenAreIndependentAndReleaseIsIdempotent() {
        var budget=new CampusAdmissionBudget(2,18);var user=UUID.randomUUID();
        var first=budget.reserve(user);var second=budget.reserve(user);
        assertThatThrownBy(()->budget.reserve(user)).isInstanceOf(CampusAdmissionBudget.Failure.class)
                .extracting(x->((CampusAdmissionBudget.Failure)x).code()).isEqualTo("USER_IN_FLIGHT_LIMIT");
        var all=new ArrayList<CampusAdmissionBudget.Reservation>();all.add(first);all.add(second);
        for(int i=0;i<16;i++)all.add(budget.reserve(UUID.randomUUID()));
        assertThat(budget.inFlight()).isEqualTo(18);
        assertThatThrownBy(()->budget.reserve(UUID.randomUUID())).isInstanceOf(CampusAdmissionBudget.Failure.class)
                .extracting(x->((CampusAdmissionBudget.Failure)x).code()).isEqualTo("INSTANCE_CAPACITY_EXCEEDED");
        first.release();first.release();assertThat(budget.inFlight()).isEqualTo(17);assertThat(budget.inFlight(user)).isEqualTo(1);
        for(var r:all)r.release();assertThat(budget.inFlight()).isZero();assertThat(budget.inFlight(user)).isZero();
    }
    @Test void concurrentSameUserCannotOverReserve()throws Exception {
        var budget=new CampusAdmissionBudget(2,18);var user=UUID.randomUUID();var held=new ConcurrentLinkedQueue<CampusAdmissionBudget.Reservation>();
        try(var callers=Executors.newFixedThreadPool(8)) {
            var results=new ArrayList<Future<?>>();var go=new CountDownLatch(1);
            for(int i=0;i<64;i++)results.add(callers.submit(()->{go.await();try{held.add(budget.reserve(user));}
                catch(CampusAdmissionBudget.Failure e){assertThat(e.code()).isEqualTo("USER_IN_FLIGHT_LIMIT");}return null;}));
            go.countDown();for(var f:results)f.get(2,TimeUnit.SECONDS);
            assertThat(held).hasSize(2);assertThat(budget.inFlight()).isEqualTo(2);assertThat(budget.inFlight(user)).isEqualTo(2);
        } finally {held.forEach(CampusAdmissionBudget.Reservation::release);}
        assertThat(budget.inFlight()).isZero();
    }
}
