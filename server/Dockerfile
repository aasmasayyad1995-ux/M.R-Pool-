# Build
FROM gradle:8.7-jdk17 AS build
WORKDIR /home/gradle/src
COPY --chown=gradle:gradle . .
RUN gradle installDist --no-daemon

# Run
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /home/gradle/src/build/install/mrpool-server/ /app/
ENV PORT=8080
EXPOSE 8080
CMD ["/app/bin/mrpool-server"]
