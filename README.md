# What?

MCOpts is a getopt / getopt_long / cli / bash oriented minecraft command parameter management api.

In short, this means your commands may look a little like this:

    /#gen Tower -msr90 --gen natural_9i249u982 -d0 -x ~2

The library especially aims to eliminate redundant data (such as the same command usage in every single language file), and shorten common command argument processes. 

This greatly reduces the susceptibility to errors and should save you time in the long run.

# Getting Started

To add MCOpts as a dependency to your mod, add this to your build.gradle:

Up above

    buildscript {

        dependencies {
            classpath 'com.github.jengelman.gradle.plugins:shadow:2.0.0'
        }
    }

    apply plugin: 'com.github.johnrengelman.shadow'

Down below

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

# Using MCOpts

Please refer to [using MCOpts](https://github.com/Ivorforce/MCOpts/wiki/Using-MCOpts) and [adapting MCOpts](https://github.com/Ivorforce/MCOpts/wiki/Adapting-MCOpts).

# License TL;DR

You are free to include the library using the steps above, which will automatically add all required files (license, notices) to your jar. If you modify it, you also have to include a changes file to outline roughly what you did.

[Full TL;DR](https://tldrlegal.com/license/apache-license-2.0-(apache-2.0))
