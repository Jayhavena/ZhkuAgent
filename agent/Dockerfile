# 使用预装 Maven 和 JDK21 的镜像
FROM maven:3.9-amazoncorretto-21
WORKDIR /app

# 只复制必要的源代码和配置文件
COPY pom.xml .
COPY agent/pom.xml ./agent/pom.xml
COPY agent/src ./agent/src

# 使用 Maven 执行打包
RUN mvn -f agent/pom.xml clean package -DskipTests

# 暴露应用端口
EXPOSE 8123

# 使用生产环境配置启动应用
CMD ["java", "-jar", "/app/agent/target/agent-0.0.1-SNAPSHOT.jar", "--spring.profiles.active=prod"]
