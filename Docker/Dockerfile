FROM eclipse-temurin:17-jre-alpine

LABEL maintainer="Jeswin Santosh jeswin.santosh@outlook.com"

WORKDIR /app

ARG JAR_PATH
ARG PROPERTIES_PATH
ARG DELAY

ENV delay ${DELAY}

RUN apk upgrade --no-cache && \
    apk add --no-cache bash tree libstdc++

COPY ${JAR_PATH} /app
COPY ${PROPERTIES_PATH}/*.properties /app

CMD ["sh", "-c", "sleep ${delay} && java -jar *.jar"]