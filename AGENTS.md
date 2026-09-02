# AGENTS.md

Guidelines for AI coding agents and developers working on this repository (Angry IP Scanner, JavaFX-based `new-features` branch).

## Mandatory: restart the application after every fix

**After applying any code fix or change, you MUST close the currently running application instance and start it again.**

- Java class files are only loaded at process start-up: a running instance keeps using the OLD code, so changes cannot be verified (or can even be misverified) while the old instance is still running.
- Steps to follow after every fix:
  1. Compile the change (e.g. `./gradlew compileJava` with JDK 21).
  2. Kill the running app instance before starting the new one (e.g. `taskkill /PID <pid> /F` on Windows), then verify via the process list that no `net.azib.ipscan.Main` java process remains.
  3. Start the application again using the usual launch command (see `run-ipscan.bat` or the dev classpath with the JavaFX 21 jars), redirect its output to a log file, and confirm the new instance is up and the fixed behavior works.
- Never leave a stale instance running while testing a new build, and never assume a fix works just because it compiles — verify it in the restarted application.

## Other conventions

- Code language: Java 21, JavaFX 21 UI (`src/net/azib/ipscan/gui/fx/`), Gradle build.
- Use JDK 21 for Gradle: `JAVA_HOME=<path-to-jdk21> ./gradlew <task>` (the default `java` on PATH may be Java 8).
- After code changes, at minimum run `./gradlew compileJava` and, when relevant, `./gradlew compileTestJava` before restarting the app.
- Use only english in comments
