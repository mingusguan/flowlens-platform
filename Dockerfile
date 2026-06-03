# syntax=docker/dockerfile:1

FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /workspace

COPY pom.xml ./
RUN mkdir -p flowlens-admin
COPY flowlens-admin/pom.xml flowlens-admin/pom.xml
RUN mvn -pl flowlens-admin -am dependency:go-offline

COPY flowlens-admin/src flowlens-admin/src
RUN mvn -pl flowlens-admin -am package -DskipTests

FROM eclipse-temurin:17-jre-jammy

ENV TZ=Asia/Shanghai
ENV PYTHONUNBUFFERED=1

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates \
        curl \
        nodejs \
        python3 \
        python3-pip \
    && ln -sf /usr/bin/python3 /usr/local/bin/python \
    && pip3 install --no-cache-dir \
        requests==2.31.0 \
        betterproto==2.0.0b6 \
        websocket-client==1.7.0 \
        PyExecJS==1.5.1 \
        mini_racer==0.12.4 \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=build /workspace/flowlens-admin/target/flowlens-admin-*.jar /app/flowlens-admin.jar
COPY collectors/douyin/saermart_adapter.py /app/collectors/douyin/saermart_adapter.py

RUN chmod +x /app/collectors/douyin/saermart_adapter.py

EXPOSE 8088

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS:-} -jar /app/flowlens-admin.jar"]
