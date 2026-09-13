# TaskForge

TaskForge is a native Java task processing system built to demonstrate
queueing, worker pools, concurrency, retries, timeouts and TCP networking.

This project is being built progressively from simple foundations to a
more complete distributed-style backend system.

## Current phase

Phase 0: project initialization.

## Requirements

- Java 21 LTS or newer
- Maven 3.9+
- Git

## Build

    mvn clean verify

## Run

    mvn compile exec:java

Or build and run the JAR:

    mvn package
    java -jar target/taskforge-0.1.0-SNAPSHOT.jar

## Test

    mvn test

## Project status

This repository is under active development.
The architecture will evolve through dedicated phases:
domain model, task queue, worker pool, retry/timeout, TCP server,
observability, Docker and CI/CD.