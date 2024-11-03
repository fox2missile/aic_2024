/*
 * This file was generated by the Gradle 'init' task.
 *
 * This is a general purpose Gradle build.
 * Learn more about Gradle by exploring our Samples at https://docs.gradle.org/8.10.2/samples
 */

plugins {
    application
}

repositories {
    mavenCentral()
}

sourceSets {
    main { 
        java {
            val srcDir = "src"
            val mainJavaSourceSet: SourceDirectorySet = sourceSets.getByName("main").java
            mainJavaSourceSet.srcDir(srcDir)
        }
    }
}

dependencies {
    implementation(files("jars/aic2024.jar"))
    implementation(files("jars/asm-all-5.0.3.jar"))
    implementation("com.github.javaparser:javaparser-core:3.26.2")
}

application {
    mainClass = "fast.Compiler"
}