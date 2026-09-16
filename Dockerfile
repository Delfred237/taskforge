# ============================================
# Stage 1: Build the application
# ============================================
FROM eclipse-temurin:21-jdk-alpine AS builder

# Installer Maven
RUN apk add --no-cache maven

# Définir le répertoire de travail
WORKDIR /build

# Copier uniquement pom.xml pour exploiter le cache Docker
COPY pom.xml .

# Télécharger les dépendances (cette couche sera mise en cache)
RUN mvn dependency:go-offline -B

# Copier le code source
COPY src ./src

# Compiler et packager
RUN mvn clean package -DskipTests -B

# ============================================
# Stage 2: Runtime image
# ============================================
FROM eclipse-temurin:21-jre-alpine AS runtime

# Créer un utilisateur non-root pour exécuter l'application
RUN addgroup -S taskforge && adduser -S taskforge -G taskforge

# Définir le répertoire de travail
WORKDIR /app

# Copier le JAR depuis le stage builder
COPY --from=builder /build/target/taskforge-0.1.0-SNAPSHOT.jar app.jar

# Changer le propriétaire des fichiers
RUN chown -R taskforge:taskforge /app

# Basculer vers l'utilisateur non-root
USER taskforge

# Exposer le port par défaut
EXPOSE 8080

# Variables d'environnement avec valeurs par défaut
ENV SERVER_PORT=8080 \
    WORKER_COUNT=4 \
    QUEUE_CAPACITY=100 \
    TASK_TIMEOUT_MS=5000 \
    MAX_RETRIES=2

# Point d'entrée
ENTRYPOINT ["java", "-jar", "app.jar"]