# Building FPP Extensions from Source

Guide for building FPP Extensions from source code.

## Prerequisites

Before building, ensure you have:

- ✅ **Java 21** (JDK 21 required)
- ✅ **Git** installed
- ✅ **Gradle** (wrapper included in project)
- ✅ **FPP 1.6.6.12.2+** (for testing)

## Quick Start

### Clone the Repository

```bash
git clone https://github.com/yourusername/fpp-extensions.git
cd fpp-extensions
```

### Build All Extensions

```bash
# On Windows
gradlew.bat clean build --no-daemon

# On Linux/Mac
./gradlew clean build --no-daemon
```

### Build Specific Extension

```bash
# Build only fpp-ping
./gradlew :fpp-ping:build --no-daemon

# Build only fpp-skin
./gradlew :fpp-skin:build --no-daemon
```

### Build Full Pack (fpp-spoof)

```bash
./gradlew :fpp-spoof:build --no-daemon
```

## Output Files

After building, find your JAR files:

### Individual Extensions

```
fpp-*/build/libs/fpp-*-1.1.0.jar
```

Examples:
- `fpp-ping/build/libs/fpp-ping-1.1.0.jar`
- `fpp-skin/build/libs/fpp-skin-1.1.0.jar`

### Full Pack

```
fpp-spoof/build/libs/fpp-spoof-1.1.0-all.jar
```

This JAR contains all extensions bundled together.

## Project Structure

```
fpp-extensions/
├── fpp-aichat/           # AI chat extension
│   ├── src/
│   ├── build.gradle.kts
│   └── ...
├── fpp-chat/             # Chat extension
├── fpp-command/          # Command extension
├── fpp-groups/           # Groups extension
├── fpp-list/             # Tab list extension
├── fpp-luckperms/        # LuckPerms integration
├── fpp-nametag/          # Nametag extension
├── fpp-peaks/            # Performance monitoring
├── fpp-ping/             # Ping spoofing
├── fpp-skin/             # Skin management
├── fpp-waypoints/        # Waypoint system
├── fpp-spoof/            # Combined pack (bundle)
├── build.gradle.kts      # Root build configuration
├── settings.gradle.kts   # Project settings
└── gradlew               # Gradle wrapper
```

## Build Configuration

### Root build.gradle.kts

```kotlin
plugins {
    java
}

allprojects {
    group = "com.fpp-extensions"
    version = "1.1.0"
}

subprojects {
    apply(plugin = "java")
    
    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }
    
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}
```

### Extension build.gradle.kts Example

```kotlin
plugins {
    id("java")
}

group = "com.fpp-extensions"
version = "1.1.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Paper API (provided - not included in JAR)
    compileOnly("io.papermc.paper:paper-api:1.21-R0.1-SNAPSHOT")
    
    // FPP API (provided - not included in JAR)
       compileOnly(files("../libs/fpp-1.6.6.12.2.jar"))
    
    // Adventure API (provided by Paper)
    compileOnly("net.kyori:adventure-api:4.14.0")
    compileOnly("net.kyori:adventure-platform-bukkit:4.3.1")
}

tasks.jar {
    archiveClassifier.set("")
}
```

### fpp-spoof build.gradle.kts (Bundle)

```kotlin
plugins {
    id("java")
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.fpp-extensions"
version = "1.1.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    // Include all extensions
    implementation(project(":fpp-aichat"))
    implementation(project(":fpp-chat"))
    implementation(project(":fpp-command"))
    implementation(project(":fpp-groups"))
    implementation(project(":fpp-list"))
    implementation(project(":fpp-luckperms"))
    implementation(project(":fpp-nametag"))
    implementation(project(":fpp-peaks"))
    implementation(project(":fpp-ping"))
    implementation(project(":fpp-skin"))
    implementation(project(":fpp-waypoints"))
}

tasks.shadowJar {
    archiveClassifier.set("all")
    
    // Merge services
    mergeServiceFiles()
}
```

## settings.gradle.kts

```kotlin
rootProject.name = "fpp-extensions"

include(":fpp-aichat")
include(":fpp-chat")
include(":fpp-command")
include(":fpp-groups")
include(":fpp-list")
include(":fpp-luckperms")
include(":fpp-nametag")
include(":fpp-peaks")
include(":fpp-ping")
include(":fpp-skin")
include(":fpp-waypoints")
include(":fpp-spoof")
```

## Common Build Tasks

### Clean Build

Removes old builds and rebuilds:

```bash
./gradlew clean build --no-daemon
```

### Build Without Tests

Skip tests for faster builds:

```bash
./gradlew build --no-daemon -x test
```

### Build with Debug Output

See detailed build logs:

```bash
./gradlew build --no-daemon --info
```

### Build Specific Task

```bash
# List all available tasks
./gradlew tasks

# Run specific task
./gradlew :fpp-ping:jar
```

### Rebuild Single Extension

```bash
./gradlew :fpp-ping:clean :fpp-ping:build --no-daemon
```

## Development Setup

### IDE Setup

#### IntelliJ IDEA

1. Open project in IntelliJ
2. Wait for Gradle import to complete
3. Set project SDK to Java 21
4. Set project language level to 21

#### Eclipse

1. Import as Gradle project
2. Right-click → Gradle → Refresh Gradle Project
3. Set Java 21 in build path

#### VS Code

1. Install Extension Pack for Java
2. Open folder in VS Code
3. Wait for Gradle import

### Adding a New Extension

1. **Create directory:**
   ```bash
   mkdir fpp-myextension
   cd fpp-myextension
   mkdir -p src/main/java/com/example/myextension
   ```

2. **Add to settings.gradle.kts:**
   ```kotlin
   include(":fpp-myextension")
   ```

3. **Create build.gradle.kts:**
   ```kotlin
   plugins {
       id("java")
   }
   
   group = "com.fpp-extensions"
   version = "1.1.0"
   
   java {
       toolchain {
           languageVersion.set(JavaLanguageVersion.of(21))
       }
   }
   
   repositories {
       mavenCentral()
       maven("https://repo.papermc.io/repository/maven-public/")
   }
   
   dependencies {
       compileOnly("io.papermc.paper:paper-api:1.21-R0.1-SNAPSHOT")
    compileOnly(files("../libs/fpp-1.6.6.12.2.jar"))
   }
   ```

4. **Create extension class:**
   ```java
   package com.example.myextension;
   
   import me.bill.fakePlayerPlugin.api.FppApi;
   import me.bill.fakePlayerPlugin.api.FppExtension;
   import org.jetbrains.annotations.NotNull;
   
   public class MyExtension implements FppExtension {
       @Override
       public @NotNull String getName() {
           return "MyExtension";
       }
       
       @Override
       public @NotNull String getVersion() {
           return "1.0.0";
       }
       
       @Override
       public void onEnable(@NotNull FppApi api) {
           // Initialization code
       }
       
       @Override
       public void onDisable() {
           // Cleanup code
       }
   }
   ```

5. **Build:**
   ```bash
   ./gradlew :fpp-myextension:build --no-daemon
   ```

## Adding to fpp-spoof Bundle

To include your new extension in the full pack:

1. **Add dependency to fpp-spoof/build.gradle.kts:**
   ```kotlin
   dependencies {
       implementation(project(":fpp-myextension"))
       // ... other extensions
   }
   ```

2. **Rebuild fpp-spoof:**
   ```bash
   ./gradlew :fpp-spoof:build --no-daemon
   ```

## Troubleshooting Builds

### Java Version Error

**Error:** `Could not determine the dependencies of task...`

**Solution:** Ensure Java 21 is installed and set as JAVA_HOME:

```bash
# Check Java version
java -version

# Set JAVA_HOME (Linux/Mac)
export JAVA_HOME=/path/to/java21

# Set JAVA_HOME (Windows)
set JAVA_HOME=C:\Program Files\Java\jdk-21
```

### FPP JAR Not Found

**Error:** `Could not find fpp-1.6.6.12.2.jar`

**Solution:** Place FPP JAR in `libs/` directory:

```bash
mkdir libs
cp /path/to/fpp-1.6.6.12.2.jar libs/
```

### Gradle Daemon Issues

**Error:** `Gradle Daemon disappeared unexpectedly`

**Solution:** Build without daemon:

```bash
./gradlew build --no-daemon
```

Or increase daemon heap size in `gradle.properties`:
```
org.gradle.jvmargs=-Xmx2g
```

### Shadow JAR Not Created

**Error:** No `-all.jar` file after build

**Solution:** Ensure shadow plugin is applied:

```kotlin
plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
}
```

And use shadowJar task:
```bash
./gradlew :fpp-spoof:shadowJar --no-daemon
```

### Dependency Conflicts

**Error:** `Duplicate class found`

**Solution:** Exclude conflicting dependencies:

```kotlin
dependencies {
    implementation(project(":fpp-chat")) {
        exclude(group = "net.kyori", module = "adventure-api")
    }
}
```

## Testing Extensions

### Local Server Testing

1. **Build extension:**
   ```bash
   ./gradlew :fpp-ping:build --no-daemon
   ```

2. **Copy to test server:**
   ```bash
   cp fpp-ping/build/libs/fpp-ping-1.1.0.jar /path/to/server/plugins/FakePlayerPlugin/extensions/
   ```

3. **Start server and test**

### Automated Testing

Add tests to `src/test/java/`:

```java
package com.example.myextension;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class MyExtensionTest {
    @Test
    public void testGetName() {
        MyExtension ext = new MyExtension();
        assertEquals("MyExtension", ext.getName());
    }
}
```

Run tests:
```bash
./gradlew test --no-daemon
```

## CI/CD Setup

### GitHub Actions Example

Create `.github/workflows/build.yml`:

```yaml
name: Build

on: [push, pull_request]

jobs:
  build:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v3
    
    - name: Set up JDK 21
      uses: actions/setup-java@v3
      with:
        java-version: '21'
        distribution: 'temurin'
    
    - name: Build with Gradle
      run: ./gradlew build --no-daemon
    
    - name: Upload artifacts
      uses: actions/upload-artifact@v3
      with:
        name: fpp-extensions
        path: fpp-*/build/libs/*.jar
```

## Publishing

### To Maven Repository

Add to `build.gradle.kts`:

```kotlin
plugins {
    id("maven-publish")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.fpp-extensions"
            artifactId = "fpp-extensions"
            version = "1.1.0"
            
            from(components["java"])
        }
    }
    
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/yourusername/fpp-extensions")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
```

Publish:
```bash
./gradlew publish --no-daemon
```

## Build Best Practices

1. **Always use `--no-daemon`** for CI/CD builds
2. **Clean before final builds** to ensure fresh compilation
3. **Test on multiple FPP versions** if supporting older versions
4. **Use shadow JAR** for bundled extensions
5. **Don't commit build artifacts** (add `build/` to `.gitignore`)
6. **Version your JARs** with semantic versioning
7. **Document breaking changes** in CHANGELOG.md

## Resources

- [Gradle Documentation](https://docs.gradle.org/)
- [Shadow Plugin Documentation](https://github.com/johnrengelman/shadow)
- [FPP API Documentation](../Extensions)
- [Java 21 Release Notes](https://openjdk.org/projects/jdk/21/)

---

## Quick Reference

```bash
# Build everything
./gradlew clean build --no-daemon

# Build specific extension
./gradlew :fpp-ping:build --no-daemon

# Build full pack
./gradlew :fpp-spoof:build --no-daemon

# Skip tests
./gradlew build -x test --no-daemon

# See build info
./gradlew build --info --no-daemon

# List tasks
./gradlew tasks
```
