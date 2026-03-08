FROM eclipse-temurin:17-jre

WORKDIR /app

# Copy the pre-built fat JAR
COPY target/chaincode.jar app.jar

# Expose the CaaS gRPC port
EXPOSE 9999

ENV CHAINCODE_SERVER_ADDRESS=0.0.0.0:9999

ENTRYPOINT ["java", "-jar", "app.jar"]
