plugins {
    `java-library`
// DO NOT use app plugin, it will mess up distZip
    distribution
    `maven-publish`
    signing
    id("io.github.linguaphylo.platforms.lphy-publish") version "0.1.2"
}

// TODO 3 versions: here, LPhyBEAST, version.xml
// version has to be manually adjusted to keep same between version.xml and here
version = "0.3.1"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
}

val beast2Jars = fileTree("lib2") {
    exclude("**/starbeast2-*.jar")
}
val notReleasedJars = fileTree("lib") {
    // not released, so must include in lphybeast release
    include("bdtree.jar")
}
val lblibs by configurations.creating {
    // Add to defaultDependencies to get their all jars
    defaultDependencies {
        add(project.dependencies.implementation("com.google.guava:guava:23.6-jre"))
        add(project.dependencies.implementation("org.jblas:jblas:1.2.3"))
        add(project.dependencies.implementation("info.picocli:picocli:4.6.2"))
    }
}

// if the project dependencies ues impl, then impl(proj(..)) will only have source code,
// which is equivalent to project-version.jar.
// if api is used, then all dependencies will pass to here,
// but do not use api unless you have to.
dependencies {
//    implementation(lblibs)

    // io.github.linguaphylo
    api("io.github.linguaphylo:lphy:1.2.0")
    // all released beast 2 libs
    api(beast2Jars)
    // other jars must be included
    implementation(notReleasedJars)

    // tests
    testImplementation("junit:junit:4.13.2")

//    testRuntimeOnly("io.github.linguaphylo:lphy:1.1.0")
//    testRuntimeOnly(beast2Jars)

//    if (project.hasProperty("isRuntime")) {
//        runtimeOnly("io.github.linguaphylo:lphy:1.1.0")
//        runtimeOnly(beast2Jars)
//    }
}

val maincls = "lphybeast.LPhyBEAST"
val developers = "LPhyBEAST developer team"
tasks.jar {
    manifest {
        // shared attr in the root build
        attributes(
            "Main-Class" to maincls,
            "Implementation-Title" to "LPhyBEAST",
            "Implementation-Vendor" to developers,
        )
    }
}

// launch lphybeast
tasks.register("runLPhyBEAST", JavaExec::class.java) {
    // use classpath
    jvmArgs = listOf("-cp", sourceSets.main.get().runtimeClasspath.asPath)
    println("clspath = ${sourceSets.main.get().runtimeClasspath.asPath}")
    mainClass.set(maincls)
    setArgs(listOf("-o", "$rootDir/tmp/RSV2.xml",
        "$rootDir/../linguaPhylo/examples/RSV2.lphy"))
}

tasks.getByName<Tar>("distTar").enabled = false
// exclude start scripts
//tasks.getByName<CreateStartScripts>("startScripts").enabled = false

// dist as a beast2 package:
// 1. never use `application` Gradle plugin;
// 2. include the task output, not the output files;
// 3. exclude lphy from lphybeast core release, because SPI does not work with BEAST class loader.
//    But for lphybeast extensions, lphy part has to be included, due to BEAST class loader.
distributions {
    main {
        contents {
//            eachFile {  println(relativePath)  }
            includeEmptyDirs = false
            into("lib") {
//                println(lblibs.files.toList())
//                println(lblibs.fileCollection(project.dependencies.implementation("com.google.guava:guava:23.6-jre")).toList())
                // all 3rd party jars
                from(lblibs.files)
                // lphybeast core jar
                from(tasks.jar)
                // bdtree
                from(notReleasedJars)
                //TODO require to run distZip after build to copy mascot jar
                from(project(":mascot").layout.buildDirectory.dir("libs")){
                    exclude("*-sources.jar")
                }
            }
            into("."){
                from("$rootDir") {
                    include("README.md")
                    include("LICENSE")
                    include("version.xml")
                }
            }
            // include src jar
            into("src") {
                from(tasks.getByName<Jar>("sourcesJar"))
                //TODO
                from(project(":mascot").layout.buildDirectory.dir("libs")){
                    include("*-sources.jar")
                }
            }
            // lphybeast script
            from("${layout.projectDirectory.dir("bin")}") {
                include("lphybeast")
                into("bin")
                eachFile {
                    // fileMode 755 not working
                    file.setExecutable(true, true)
                }
            }
        }
    }
}

// beast 2 will remove version from Zip file name, and then decompress
// rm lphybeast-$version from the relative path of files inside Zip to make it working
tasks.withType<Zip>() {
    doFirst {
        if ( name.equals("distZip") ) {
            // only activate in distZip, otherwise will affect all jars and zips,
            // e.g. main class not found in lphybeast-$version.jar.
            eachFile {
                relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray())
                println(relativePath)
            }
        }
    }
}

val webSteam = "github.com/LinguaPhylo/LPhyBeast"
publishing {
    publications {
        // project.name contains "lphy" substring
        create<MavenPublication>(project.name) {
            artifactId = project.base.archivesName.get()
            artifact(tasks.distZip.get())
            // Configures the version mapping strategy
            versionMapping {
                usage("java-api") {
                    fromResolutionOf("runtimeClasspath")
                }
                usage("java-runtime") {
                    fromResolutionResult()
                }
            }
            pom {
                name.set(project.name)
                description.set(
                    "A command-line program that takes an LPhy model specification " +
                            "including a data block, and produces a BEAST 2 XML input file."
                )
                // compulsory
                url.set("https://linguaphylo.github.io/")
                packaging = "zip"
                developers {
                    developer {
                        name.set(developers)
                    }
                }
                properties.set(
                    mapOf(
                        "maven.compiler.source" to java.sourceCompatibility.majorVersion,
                        "maven.compiler.target" to java.targetCompatibility.majorVersion
                    )
                )
                licenses {
                    license {
                        name.set("GNU Lesser General Public License, version 3")
                        url.set("https://www.gnu.org/licenses/lgpl-3.0.txt")
                    }
                }
                // https://central.sonatype.org/publish/requirements/
                scm {
                    connection.set("scm:git:git://${webSteam}.git")
                    developerConnection.set("scm:git:ssh://${webSteam}.git")
                    url.set("https://${webSteam}")
                }
            }
            println("Define MavenPublication ${name} and set shared contents in POM")
        }
    }
}

tasks.test {
    useJUnit()
    // useJUnitPlatform()
    // set heap size for the test JVM(s)
    minHeapSize = "128m"
    maxHeapSize = "1G"
    // show standard out and standard error of the test JVM(s) on the console
    testLogging.showStandardStreams = true
    //testLogging.exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    exclude("**/Tutorial*")
}

