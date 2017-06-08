# Getting Started

To add MCOpts as a dependency to your mod, add this to your build.gradle:

    // At the top
    buildscript {

        dependencies {
            classpath 'com.github.jengelman.gradle.plugins:shadow:2.0.0'
        }
    }

    apply plugin: 'com.github.johnrengelman.shadow'
    
    // Down below

    dependencies {
        deobfCompile 'ivorius.mcopts:MCOpts:0.9' // Change the version accordingly, obviously
        // Find versions over at http://files.minecraftforge.net/maven/ivorius/mcopts/MCOpts/index.html
    }

    shadowJar {
        exclude 'META-INF/*', 'META-INF/maven/**'
        relocate 'ivorius.mcopts', project.group + '.shadow.mcopts'
        classifier=''
    }

    reobf {
        shadowJar { mappingType = 'SEARGE' }
    }

    tasks.build.dependsOn reobfShadowJar

Now, re-setup your workspace:

    gradle setupDecompWorkspace
    
If you use IDEA, remember to refresh your gradle project too. You will need at least Gradle 3.0, so set up your gradle home correctly.

And that's it! MCOpts is now included in your jar.
