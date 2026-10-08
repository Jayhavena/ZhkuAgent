package com.zhku.knowledge.controlled;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import static com.zhku.knowledge.controlled.ControlledCampusTestFixtures.*;
import static org.assertj.core.api.Assertions.*;

/** 真正的socket响应截断，不把直接丢弃返回对象冒充网络故障。 */
@Timeout(45)
class SubmissionResponseLossHttpTest {
    @Test void committedPostResponseTruncatedThenGetAndReplayFindSameRunWithoutDispatch() throws Exception {
        var data=seed(); var model=new Model();
        var threads=Executors.newFixedThreadPool(2); HttpServer proxy=null;
        var postResponses=new AtomicInteger(); var committed=new CountDownLatch(1);
        try(var app=start(data,model);var upstream=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build()) {
            proxy=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);proxy.setExecutor(threads);
            proxy.createContext("/", exchange->{
                try {
                    var method=exchange.getRequestMethod();var bytes=exchange.getRequestBody().readAllBytes();
                    var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+app.port+exchange.getRequestURI())).timeout(Duration.ofSeconds(5));
                    exchange.getRequestHeaders().forEach((name,values)->{
                        if(!java.util.Set.of("host","connection","content-length","upgrade","http2-settings").contains(name.toLowerCase()))
                            values.forEach(value->builder.header(name,value));
                    });
                    var response=upstream.send(builder.method(method,HttpRequest.BodyPublishers.ofByteArray(bytes)).build(),HttpResponse.BodyHandlers.ofByteArray());
                    if(method.equals("POST")&&exchange.getRequestURI().getPath().equals(PREFIX+"/runs")&&postResponses.incrementAndGet()==1) {
                        assertThat(response.statusCode()).isEqualTo(202);committed.countDown();
                        // 发送完整状态头后截断应有的正文，客户端不能把它当成正常JSON或安全重试。
                        exchange.sendResponseHeaders(202,response.body().length+100);return;
                    }
                    response.headers().map().forEach((name,values)->{
                        if(!java.util.Set.of("connection","content-length","transfer-encoding").contains(name.toLowerCase()))
                            exchange.getResponseHeaders().put(name,values);
                    });
                    exchange.sendResponseHeaders(response.statusCode(),response.statusCode()==204?-1:response.body().length);
                    if(response.statusCode()!=204)exchange.getResponseBody().write(response.body());
                }catch(Exception failure){ throw new IllegalStateException("合成代理处理失败",failure); }
                finally {exchange.close();}
            });proxy.start();
            try(var a=new User(proxy.getAddress().getPort())) {
                assertThat(a.login(data.a()).statusCode()).isEqualTo(200);var key=UUID.randomUUID();
                assertThatThrownBy(()->a.submitWithKey(QUESTION,COURSES,key)).isInstanceOf(java.io.IOException.class);
                assertThat(committed.await(2,TimeUnit.SECONDS)).isTrue();assertThat(postResponses).hasValue(1);
                var confirmed=a.send("GET",PREFIX+"/submissions/"+key,null,false);assertThat(confirmed.statusCode()).isEqualTo(200);
                var id=UUID.fromString(JSON.readTree(confirmed.body()).path("runId").asText());a.awaitTerminal(id);
                var replay=a.submitWithKey(QUESTION,COURSES,key);assertThat(replay.statusCode()).isEqualTo(200);
                assertThat(JSON.readTree(replay.body()).path("runId").asText()).isEqualTo(id.toString());
                assertThat(model.calls).hasValue(2);assertThat(model.active).hasValue(0);
                assertThat(((ThreadPoolExecutor)app.context.getBean("controlledCampusWorker")).getTaskCount()).isEqualTo(1);
                try(var c=data.source().getConnection();var p=c.prepareStatement("SELECT count(*),count(DISTINCT run_id) FROM campus_agent_run_submission WHERE owner_user_id=? AND submission_key=?")) {
                    p.setObject(1,data.a().id());p.setObject(2,key);try(var rows=p.executeQuery()){rows.next();assertThat(rows.getInt(1)).isEqualTo(1);assertThat(rows.getInt(2)).isEqualTo(1);}
                }
                System.out.println("3C-HTTP-A5 truncatedSocket=true submissions=1 runs=1 dispatches=1 modelRequests=2");
            }
        }finally {
            if(proxy!=null)proxy.stop(0);threads.shutdownNow();assertThat(threads.awaitTermination(2,TimeUnit.SECONDS)).isTrue();cleanup(data);
        }
    }
}
