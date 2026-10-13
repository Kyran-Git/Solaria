# Java environment

Minecraft 26.1.2 (and therefore this mod) requires **Java 25**.

The default JDK on this machine is Temurin 21 (`JAVA_HOME=C:\Users\nikla\.jabba\jdk\temurin@21.0`),
which is why `gradlew runClient` failed before.

## How it is solved

`build.gradle` declares a Gradle **toolchain**:

```groovy
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}
```

Gradle auto-detects the installed JDK 25 (`C:\Program Files\Java\jdk-25`) and uses it for
`compileJava`, `runClient` and `runServer`, no matter which JDK Gradle itself runs on.
So `.\gradlew.bat runClient` now works out of the box.

## IntelliJ IDEA

The IDE used to run the *Minecraft Client* configuration on Java 21, which failed. This is now
fixed in the project files:

* `.idea/misc.xml` – project SDK is the registered JDK **`25`** (`C:\Program Files\Java\jdk-25`).
* `.idea/gradle.xml` – *Gradle JVM* is **`25`** instead of `#JAVA_HOME` (which points to Temurin 21).
* `.idea/runConfigurations/*.xml` – *Minecraft Client*, *Minecraft Server* and *Data Generation*
  explicitly use the alternative JRE **`25`**.

After pulling these changes, reload the Gradle project (Gradle tool window → *Reload*) so the
modules are re-imported with Java 25. If the SDK named `25` is missing on another machine, add a
JDK 25 under *File | Project Structure | SDKs* with that name, or simply re-run the Loom
`genIntellijRuns`/Gradle sync.

If the run configurations disappear or stop working, you can always use the Gradle tasks
`runClient` / `runServer` from the Gradle tool window, which use the toolchain above.

## If Java 25 is missing

Install a JDK 25 (e.g. Temurin) and either let Gradle auto-detect it or set:

```
org.gradle.java.installations.paths=C:\\Path\\To\\jdk-25
```

in `gradle.properties`.
