package com.zhku.knowledge.controlled;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
/** 诊断打包产品Context，供应商只构造、绝不发送请求。 */
@Timeout(40)
class SubmissionProductionStartupTest {
    @Test void packagedProductFactoryAndContextStartWithoutModelCalls()throws Exception {
        com.zhku.knowledge.campusagent.archive.ArchiveTestDatabase.migrate();
        var jar=Path.of("target/knowledge-0.1.0-rc.1-controlled.jar").toAbsolutePath();assertThat(Files.exists(jar)).isTrue();
        // 只挂载这一个无配置探针，不能让整个test-classes参与产品扫描并制造假bean冲突。
        var isolated=Path.of("target/isolated-submission-probe").toAbsolutePath();
        var relative=Path.of(SubmissionProductionStartupProbe.class.getName().replace('.','/')+".class");
        Files.createDirectories(isolated.resolve(relative).getParent());
        Files.copy(Path.of("target/test-classes").resolve(relative),isolated.resolve(relative),StandardCopyOption.REPLACE_EXISTING);
        var command=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),"-Dloader.main="+SubmissionProductionStartupProbe.class.getName(),
                "-Dloader.path="+isolated,"-cp",jar.toString(),"org.springframework.boot.loader.launch.PropertiesLauncher").redirectErrorStream(true);
        var process=command.start();String output;
        try(var reader=Executors.newSingleThreadExecutor()) {
            var text=reader.submit(()->{try(var lines=new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream(),StandardCharsets.UTF_8))){
                var kept=new java.util.ArrayList<String>();for(String line;(line=lines.readLine())!=null;)if(line.startsWith("PRODUCTION_PROBE_")&&kept.size()<16)kept.add(line);return String.join("\n",kept);}});
            try{assertThat(process.waitFor(20,TimeUnit.SECONDS)).as("固定产品启动/关闭20秒").isTrue();output=text.get(2,TimeUnit.SECONDS);}
            finally{if(process.isAlive()){process.destroyForcibly();assertThat(process.waitFor(5,TimeUnit.SECONDS)).isTrue();}reader.shutdown();assertThat(reader.awaitTermination(2,TimeUnit.SECONDS)).isTrue();}
        }
        System.out.println(output);assertThat(process.exitValue()).as(output).isZero();assertThat(output).contains("PRODUCTION_PROBE_READY");
    }
}
