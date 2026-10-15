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
# 显式给堆设上限（诊断 #62 code review 发现）：JDK 默认按 UseContainerSupport +
# MaxRAMPercentage=25% ergonomics 算堆大小——给 backend 容器设 mem_limit 之前，
# 容器能看到宿主机全部内存，堆隐式按「宿主机内存×25%」算；设了 mem_limit 之后，
# 这个百分比改成按「容器内存×25%」算，mem_limit 越小这个隐式缩水越狠（例如
# docker-compose.prod.yml 里的 1g 限制会让默认堆缩到 256MB）。显式设成按容器内存
# 65% 算堆，留约 35% 给 metaspace/线程栈/direct buffer/GC 开销，不依赖这套在小
# 容器下会严重缩水的默认值。
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=65.0", "-jar", "app.jar"]
