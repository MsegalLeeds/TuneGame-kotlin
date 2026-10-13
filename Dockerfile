FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

COPY . .

RUN chmod +x gradlew
RUN ./gradlew :server:installDist --no-daemon


FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/server/build/install/server /app/server

ENV PORT=10000

EXPOSE 10000

CMD ["/app/server/bin/server"]