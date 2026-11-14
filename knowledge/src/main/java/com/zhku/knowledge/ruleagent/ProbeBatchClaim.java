package com.zhku.knowledge.ruleagent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
/** 只记录批次身份，不记录凭据；失败也消耗授权，换进程或checkout不能恢复同包额度。 */
final class ProbeBatchClaim {
 private ProbeBatchClaim(){}
 static void acquire(Path state,String packageSha)throws IOException {
  if(packageSha==null||!packageSha.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("批次摘要非法");
  Files.createDirectories(state);
  Files.writeString(state.resolve(packageSha+".used"),"packageSha256="+packageSha+"\nstartedAt="+Instant.now()+"\npid="+ProcessHandle.current().pid()+"\nmaximumProviderCalls=10\n",
      StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
 }
}
