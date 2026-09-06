FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
# 本地开发机如果处于会做 TLS 中间人拦截的公司网络/代理下（如 Netskope 等 CASB），
# 访问 Maven Central 会报 certificate_unknown/PKIX 校验失败。把代理签发的根证书
# （.pem/.crt）放进仓库里的 docker-dev-ca-certs/ 目录后，这一步会自动导入 JDK 的
# cacerts 信任它们；目录为空时这一步是空操作。这个信任只影响本构建阶段，不会带入
# 最终运行镜像。
COPY docker-dev-ca-certs/ /tmp/dev-ca-certs/
RUN for f in /tmp/dev-ca-certs/*.crt /tmp/dev-ca-certs/*.pem; do \
      [ -f "$f" ] || continue; \
      keytool -importcert -noprompt -trustcacerts -alias "$(basename "$f")" \
        -file "$f" -keystore "$JAVA_HOME/lib/security/cacerts" -storepass changeit; \
    done
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/tuoguan-backend-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
