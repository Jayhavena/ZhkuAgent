package com.zhku.knowledge.campusagent.submission;

import java.lang.reflect.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

/** 真实归档读线程与临时数据库：owned读允许有限等待，旧匿名立即背压另由旧预算测试保持。 */
@Timeout(15)
class CampusSubmissionReadBudgetTest {
    @Test void fifthOwnedReadWaitsWithoutCreatingFifthDatabaseThread()throws Exception {
        var entered=new CountDownLatch(4);var release=new CountDownLatch(1);var block=new AtomicBoolean(true);
        var opened=new AtomicInteger();var physical=new AtomicInteger();var maximum=new AtomicInteger();
        try(var e=new CampusRunSubmissionStoreIntegrationTest.Env(source->blocking(source,entered,release,block,opened,physical,maximum));
                var clients=Executors.newFixedThreadPool(5)) {
            try {
                var first=new ArrayList<Future<?>>();for(int i=0;i<4;i++)first.add(clients.submit(()->e.store.findSubmissionOwned(e.a,UUID.randomUUID())));
                assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();
                var fifth=clients.submit(()->e.store.findSubmissionOwned(e.b,UUID.randomUUID()));
                assertThatThrownBy(()->fifth.get(100,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                assertThat(opened).hasValue(4);block.set(false);release.countDown();
                for(var read:first)assertThat(read.get(2250,TimeUnit.MILLISECONDS)).isEqualTo(Optional.empty());
                assertThat(fifth.get(2250,TimeUnit.MILLISECONDS)).isEqualTo(Optional.empty());assertThat(maximum.get()).isLessThanOrEqualTo(4);
            }finally{block.set(false);release.countDown();}
        }
        awaitPhysicalExit(physical);
    }

    @Test void closingWhileOwnedReaderWaitsDoesNotStartItOrFreeStuckPhysicalConnections()throws Exception {
        var entered=new CountDownLatch(4);var release=new CountDownLatch(1);var block=new AtomicBoolean(true);
        var opened=new AtomicInteger();var physical=new AtomicInteger();var maximum=new AtomicInteger();
        try(var e=new CampusRunSubmissionStoreIntegrationTest.Env(source->blocking(source,entered,release,block,opened,physical,maximum));
                var clients=Executors.newFixedThreadPool(5)) {
            try {
                for(int i=0;i<4;i++)clients.submit(()->e.store.findOwned(e.a,UUID.randomUUID()));
                assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();var fifth=clients.submit(()->e.store.findSubmissionOwned(e.a,UUID.randomUUID()));
                assertThatThrownBy(()->fifth.get(100,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                e.store.close(Duration.ZERO);
                assertThatThrownBy(()->fifth.get(425,TimeUnit.MILLISECONDS)).isInstanceOf(ExecutionException.class);
                assertThat(opened).hasValue(4);assertThat(physical).hasValue(4);
            }finally{block.set(false);release.countDown();clients.shutdownNow();assertThat(clients.awaitTermination(2250,TimeUnit.MILLISECONDS)).isTrue();}
        }
        // caller的Future可先失败；必须另外等实际Connection.close返回。
        awaitPhysicalExit(physical);
    }

    @Test void only32OwnedReadersMayWaitAndCloseReleasesAllWaiters()throws Exception {
        var entered=new CountDownLatch(4);var release=new CountDownLatch(1);var block=new AtomicBoolean(true);
        var opened=new AtomicInteger();var physical=new AtomicInteger();var maximum=new AtomicInteger();
        try(var e=new CampusRunSubmissionStoreIntegrationTest.Env(source->blocking(source,entered,release,block,opened,physical,maximum));
                var clients=Executors.newFixedThreadPool(36)) {
            try {
                for(int i=0;i<4;i++)clients.submit(()->e.store.findOwned(e.a,UUID.randomUUID()));
                assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();
                var waiting=new ArrayList<Future<?>>();var ready=new CountDownLatch(32);var threads=ConcurrentHashMap.<Thread>newKeySet();
                for(int i=0;i<32;i++)waiting.add(clients.submit(()->{threads.add(Thread.currentThread());ready.countDown();return e.store.findSubmissionOwned(e.a,UUID.randomUUID());}));
                assertThat(ready.await(425,TimeUnit.MILLISECONDS)).isTrue();long until=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(425);
                while(threads.stream().anyMatch(t->t.getState()!=Thread.State.TIMED_WAITING)&&System.nanoTime()<until)Thread.sleep(1);
                assertThat(waiting).allMatch(f->!f.isDone());long began=System.nanoTime();
                assertThatThrownBy(()->e.store.findSubmissionOwned(e.b,UUID.randomUUID()))
                        .isInstanceOf(com.zhku.knowledge.campusagent.archive.CampusRunArchive.Failure.class)
                        .extracting(x->((com.zhku.knowledge.campusagent.archive.CampusRunArchive.Failure)x).code())
                        .isEqualTo(com.zhku.knowledge.campusagent.archive.CampusRunArchive.Code.ARCHIVE_BUSY);
                assertThat(Duration.ofNanos(System.nanoTime()-began)).isLessThan(Duration.ofMillis(425));
                e.store.close(Duration.ZERO);for(var f:waiting)assertThatThrownBy(()->f.get(425,TimeUnit.MILLISECONDS)).isInstanceOf(ExecutionException.class);
                assertThat(opened).hasValue(4);assertThat(physical).hasValue(4);
            }finally{block.set(false);release.countDown();clients.shutdownNow();assertThat(clients.awaitTermination(2250,TimeUnit.MILLISECONDS)).isTrue();}
        }
        awaitPhysicalExit(physical);
    }

    private static void awaitPhysicalExit(AtomicInteger physical)throws Exception {
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
        while(physical.get()!=0&&System.nanoTime()<until)Thread.sleep(1);
        assertThat(physical).hasValue(0);
    }

    private static DataSource blocking(DataSource original,CountDownLatch entered,CountDownLatch release,AtomicBoolean block,
            AtomicInteger opened,AtomicInteger physical,AtomicInteger maximum) {
        return (DataSource)Proxy.newProxyInstance(DataSource.class.getClassLoader(),new Class[]{DataSource.class},(proxy,method,args)->{
            if(!method.getName().equals("getConnection"))return invoke(original,method,args);
            opened.incrementAndGet();maximum.accumulateAndGet(physical.incrementAndGet(),Math::max);
            if(block.get()){entered.countDown();while(release.getCount()!=0){try{release.await();}catch(InterruptedException ignored){}}}
            var connection=invoke(original,method,args);
            return Proxy.newProxyInstance(java.sql.Connection.class.getClassLoader(),new Class[]{java.sql.Connection.class},(p,m,a)->{
                try{return invoke(connection,m,a);}finally{if(m.getName().equals("close"))physical.decrementAndGet();}
            });
        });
    }
    private static Object invoke(Object object,Method method,Object[]args)throws Throwable {
        try{return method.invoke(object,args);}catch(InvocationTargetException e){throw e.getCause();}
    }
}
