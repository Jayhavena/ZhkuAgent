package com.zhku.knowledge.ruleagent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import java.lang.reflect.InvocationTargetException;
import static org.assertj.core.api.Assertions.*;
/** 用隔离临时根和有标记的假Maven验证入口副作用，不运行真实构建/模型/数据库。 */
class RuleAgentRequestPathTest {
 static final String NEW="docs/evidence/rule-agent-provider/2026-10-15/t05-62-eligibility-request/request-package.json";
 @TempDir Path scratch;
 Path root(){Path p=Path.of("").toAbsolutePath();while(!Files.exists(p.resolve("knowledge/pom.xml")))p=p.getParent();return p;}
 @Test void hostBindsNewDirectoryWithoutReusingClosed55()throws Exception{var f=RuleAgentOnlineProbeHost.class.getDeclaredField("PACKAGE");f.setAccessible(true);assertThat(f.get(null)).isEqualTo(NEW);}
 @Test void hostRejectsExistingTargetBeforeReadingAnyAssets()throws Exception{
  Path target=scratch.resolve(NEW);assertThat(target.normalize().startsWith(scratch.normalize())).isTrue();Files.createDirectories(target.getParent());Files.writeString(target,"ORIGINAL_TARGET");
  var m=RuleAgentOnlineProbeHost.class.getDeclaredMethod("prepare",Path.class);m.setAccessible(true);
  assertThatThrownBy(()->m.invoke(null,scratch)).isInstanceOfSatisfying(InvocationTargetException.class,e->assertThat(e.getCause()).isInstanceOf(FileAlreadyExistsException.class));assertThat(Files.readString(target)).isEqualTo("ORIGINAL_TARGET");
 }
 @Test void launcherRejectsExistingNewTargetBeforeInvokingBuilder()throws Exception{
  Path scripts=scratch.resolve("knowledge/scripts");Files.createDirectories(scripts);Files.createDirectories(scratch.resolve("scripts"));
  Files.copy(root().resolve("knowledge/scripts/start-rule-agent-probe.ps1"),scripts.resolve("start-rule-agent-probe.ps1"));Files.copy(root().resolve("scripts/Invoke-ZhkuOwnedProcess.ps1"),scratch.resolve("scripts/Invoke-ZhkuOwnedProcess.ps1"));
  Path target=scratch.resolve(NEW);Files.createDirectories(target.getParent());Files.writeString(target,"ORIGINAL_TARGET");
  Files.writeString(scratch.resolve("mvnw.cmd"),String.join(System.lineSeparator(),"@echo off","echo BUILDER_CALLED>builder-called.txt","exit /b 99",""));
  // 先证明受控构建器确实会留标记，避免无效stub让“未执行构建”产生假阳性。
  var fake=new ProcessBuilder(System.getenv("ComSpec"),"/d","/c","mvnw.cmd").directory(scratch.toFile()).redirectErrorStream(true).start();
  try{assertThat(fake.waitFor(10,TimeUnit.SECONDS)).isTrue();assertThat(fake.exitValue()).isEqualTo(99);assertThat(scratch.resolve("builder-called.txt")).exists();}finally{if(fake.isAlive())fake.destroyForcibly();}
  Files.delete(scratch.resolve("builder-called.txt"));
  Path ps=Path.of(System.getenv("USERPROFILE"),".cache/codex-runtimes/codex-primary-runtime/dependencies/native/powershell/pwsh.exe");assertThat(ps).exists();
  var builder=new ProcessBuilder(ps.toString(),"-NoProfile","-File",scripts.resolve("start-rule-agent-probe.ps1").toString(),"-PrepareProbe").directory(scratch.toFile()).redirectErrorStream(true);
  builder.environment().keySet().removeIf(k->k.startsWith("DASHSCOPE_")||k.startsWith("ZHKU_")||k.startsWith("POSTGRES_"));
  var process=builder.start();try{assertThat(process.waitFor(20,TimeUnit.SECONDS)).isTrue();String output=new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);assertThat(process.exitValue()).as(output).isNotZero();assertThat(output).contains("请求包已存在，保留原包，不构建或覆盖");assertThat(scratch.resolve("builder-called.txt")).doesNotExist();assertThat(Files.readString(target)).isEqualTo("ORIGINAL_TARGET");}finally{if(process.isAlive()){process.descendants().forEach(ProcessHandle::destroyForcibly);process.destroyForcibly();process.waitFor(2,TimeUnit.SECONDS);}}
 }
}
