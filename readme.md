
#### Gradle build

In an application project create file `build.gradle`:

```groovy
buildscript {
    ext.kotlin_version = "1.3.31"

    repositories {
        mavenCentral()
    }

    dependencies {
        classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version"
    }
}

ext.ADK_ROOT = gradle.ADK_ROOT

subprojects {
    apply plugin: "java"
    apply plugin: "kotlin"

    repositories {
        mavenCentral()
    }

    compileKotlin {
        kotlinOptions.jvmTarget = "11"
    }

    dependencies {
        compile "org.jetbrains.kotlin:kotlin-stdlib"
        compile "org.jetbrains.kotlin:kotlin-stdlib-jdk7"
        compile "org.jetbrains.kotlin:kotlin-stdlib-jdk8"
        compile "org.jetbrains.kotlin:kotlin-reflect"
    }

    def buildName = project.path.substring(1).replaceAll(':', '-')
    buildDir = "${rootProject.buildDir}/subprojects/$buildName"

    compileJava {
        dependsOn(compileKotlin)
        destinationDir = compileKotlin.destinationDir
        doFirst {
            options.compilerArgs = ["--module-path", classpath.asPath]
            classpath = files()
        }
    }

    sourceSets {
        main {
            kotlin {
                srcDirs = ["src"]
            }
            java {
                srcDirs = ["src"]
                compileClasspath = main.compileClasspath
            }
        }
    }
}
```

`settings.gradle`:
```groovy
rootProject.name = "MyProject"

if (!hasProperty("ADK_ROOT")) {
    ext.ADK_ROOT = System.env.ADK_ROOT
}
gradle.ext.ADK_ROOT = ADK_ROOT

void AdkModules(List<String> modules)
{
    for (name in modules) {
        include(":$name")
        def relPath = name.replaceAll(":", "/")
        project(":$name").projectDir = file("$ADK_ROOT/$relPath")
    }
}

AdkModules(["omm", "json", "adk"])
include ":main"
```

`ADK_ROOT` can be specified via environment variable or in `gradle.properties`.
