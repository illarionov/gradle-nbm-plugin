package org.gradle.plugins.nbm

import groovy.transform.PackageScope
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.FileTreeElement
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetOutput
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar

import javax.inject.Inject

import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock

import static org.gradle.plugins.nbm.NbmPlugin.BUNDLE_CONFIGURATION_NAME
import static org.gradle.plugins.nbm.NbmPlugin.IMPLEMENTATION_CONFIGURATION_NAME
import static org.gradle.plugins.nbm.NbmPlugin.MANIFEST_TASK
import static org.gradle.plugins.nbm.NbmPlugin.NBM_TASK
import static org.gradle.plugins.nbm.NbmPlugin.NETBEANS_TASK
import static org.gradle.plugins.nbm.NbmPlugin.PROVIDED_COMPILE_CONFIGURATION_NAME
import static org.gradle.plugins.nbm.NbmPlugin.PROVIDED_RUNTIME_CONFIGURATION_NAME

@PackageScope
class NmbPluginTaskConfigurer {
    final Project project
    final ProjectLayout projectLayout
    final ProviderFactory providers
    final ObjectFactory objects
    final FileSystemOperations fileSystemOperations

    final NbmPluginExtension nbmExtension

    Configuration provideCompileConfiguration
    Configuration provideRuntimeConfiguration
    Configuration implementationConfiguration
    Configuration bundleConfiguration

    @Inject
    NmbPluginTaskConfigurer(Project project,
                            ProjectLayout projectLayout,
                            ProviderFactory providers,
                            ObjectFactory objects,
                            FileSystemOperations fileSystemOperations) {
        this.project = project
        this.projectLayout = projectLayout
        this.providers = providers
        this.objects = objects
        this.fileSystemOperations = fileSystemOperations

        nbmExtension = project.extensions.create("nbm", NbmPluginExtension, project, Clock.systemUTC())
    }

    @SuppressWarnings('UnusedVariable')
    void apply() {
        TaskProvider<Jar> jarTaskProvider = project.tasks.named("jar", Jar)
        Provider<String> moduleJarFilename = nbmExtension.moduleName.map {
            it.replace('.', '-') + '.jar'
        }

        configureConfigurations()
        project.plugins.withType(JavaPlugin) {
            TaskProvider<MergePropertiesTask> mergePropertiesTaskProvider = this.setupMergePropertiesTask(jarTaskProvider)
        }
        TaskProvider<ModuleManifestTask> manifestTaskTaskProvider = setupGenerateModuleManifestTask(jarTaskProvider)
        TaskProvider<NetBeansTask> netbeansTaskProvider = setupNetbeansTask(jarTaskProvider, moduleJarFilename)
        TaskProvider<NbmTask> nbmTaskProvider = setupNbmTask(netbeansTaskProvider, moduleJarFilename)

        project.tasks.named("assemble").configure {
            dependsOn nbmTaskProvider
        }

        addRunTask(netbeansTaskProvider, 'run', false)
        addRunTask(netbeansTaskProvider, 'debug', true)
    }

    void configureConfigurations() {
        ConfigurationContainer container = project.configurations
        provideCompileConfiguration = container.create(PROVIDED_COMPILE_CONFIGURATION_NAME)
            .setVisible(false)
            .setDescription("Additional compile classpath for libraries that should not be part of the NBM archive.");
        provideRuntimeConfiguration = container.create(PROVIDED_RUNTIME_CONFIGURATION_NAME)
            .setVisible(false)
            .extendsFrom(provideCompileConfiguration)
            .setDescription("Additional runtime classpath for libraries that should not be part of the NBM archive.");
        implementationConfiguration = container.create(IMPLEMENTATION_CONFIGURATION_NAME)
            .setVisible(false)
            .setDescription("NBM module's implementation dependencies")
        bundleConfiguration = container.create(BUNDLE_CONFIGURATION_NAME)
            .setVisible(false)
            .setDescription("NBM module's dependencies on OSGi bundles");

        container.getByName(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME)
            .extendsFrom(provideCompileConfiguration)
            .extendsFrom(implementationConfiguration)
            .extendsFrom(bundleConfiguration);
        container.getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME)
            .extendsFrom(provideRuntimeConfiguration);
    }

    TaskProvider<MergePropertiesTask> setupMergePropertiesTask(TaskProvider<Jar> jarTaskProvider) {
        def generatedClasses = projectLayout.buildDirectory.dir('generated-resources/main')
        def generatedResources = projectLayout.buildDirectory.dir('generated-resources/resources')
        def generatedOutput = projectLayout.buildDirectory.dir('generated-resources/output')

        def compileJavaTask = project.tasks.named('compileJava')
        def processResourcesTask = project.tasks.named('processResources')
        SourceSetOutput mainSourceSetOutput = project.sourceSets.main.output

        def mergeTask = project.tasks.register('mergeProperties', MergePropertiesTask) {
            inputDirectories.add(generatedClasses)
            inputDirectories.add(generatedResources)
            outputDir = generatedOutput
            dependsOn compileJavaTask, processResourcesTask
        }

        mainSourceSetOutput.dir(mergeTask.map { it.outputDir })

        jarTaskProvider.configure {
            dependsOn mergeTask
        }

        compileJavaTask.configure { compileTask ->
            compileTask.outputs.dir(generatedClasses)
            compileTask.doLast { JavaCompile it ->
                mainSourceSetOutput.classesDirs.each { projectClassDir ->
                    fileSystemOperations.copy {
                        from projectClassDir
                        into generatedClasses
                        include '**/*.properties'
                        includeEmptyDirs = false
                    }
                    project.fileTree(dir: projectClassDir)
                        .include('**/*.properties')
                        .visit { FileTreeElement details ->
                            if (!details.isDirectory()) {
                                fileSystemOperations.delete {
                                    delete details.file
                                }
                            }
                        }
                }
            }
        }

        processResourcesTask.configure { resourcesTask ->
            resourcesTask.outputs.dir generatedResources
            resourcesTask.doLast { Copy it ->
                def propertyFiles = project.fileTree(dir: mainSourceSetOutput.resourcesDir)
                    .include('**/*.properties')

                fileSystemOperations.copy {
                    from propertyFiles
                    into generatedResources
                    includeEmptyDirs = false
                }
                propertyFiles.visit { FileTreeElement details ->
                    if (!details.isDirectory()) {
                        fileSystemOperations.delete {
                            delete details.file
                        }
                    }
                }
            }
        }
        return mergeTask
    }

    TaskProvider<ModuleManifestTask> setupGenerateModuleManifestTask(TaskProvider<Jar> jarTaskProvider) {
        def manifestTask = project.tasks.register(MANIFEST_TASK, ModuleManifestTask) { task ->
            def moduleManifestConfig = objects.newInstance(ModuleManifestConfig).tap {
                moduleName = nbmExtension.moduleName
                requires = nbmExtension.requires
                localizedBundle = nbmExtension.localizingBundle
                buildVersion = nbmExtension.buildVersion
                implementationVersion = nbmExtension.implementationVersion
                specificationVersion = nbmExtension.specificationVersion
                publicPackages = providers.provider { nbmExtension.publicPackages.entries }
                moduleFriends = providers.provider { nbmExtension.moduleFriends.entries }
                layer = nbmExtension.layer
                javaDependency = nbmExtension.javaDependency
                autoupdateShowInClient = nbmExtension.autoupdateShowInClient
                moduleInstall = nbmExtension.moduleInstall
            }
            task.setModuleManifestConfig(moduleManifestConfig)
            task.generatedManifestFile = nbmExtension.generatedManifestFile
            task.netbeansClasspathExtFolder = nbmExtension.classpathExtFolder
            task.netbeansClasspath.setFrom providers.provider { getNetbeansClasspath() }
            task.runtimeConfiguration = project.configurations.findByName(project.sourceSets.main.runtimeClasspathConfigurationName)
            task.nbImplementationConfiguration = this.implementationConfiguration
            task.bundleConfiguration = this.bundleConfiguration
        }

        def userManifest = project.file("src/main/nbm/manifest.mf")
        jarTaskProvider.configure { Jar jar ->
            if (userManifest.exists()) {
                jar.manifest.from { userManifest }
            }
            jar.manifest.from manifestTask.flatMap { it.generatedManifestFile }
            jar.dependsOn(manifestTask)
        }

        project.afterEvaluate {
            if (!project.tasks.withType(ModuleManifestTask).isEmpty()) {
                if (!jarTaskProvider.get().manifest.attributes.containsKey('OpenIDE-Module-Name')) {
                    jarTaskProvider.get().manifest.attributes['OpenIDE-Module-Name'] = nbmExtension.moduleName.get()
                }
            }
        }
        return manifestTask
    }

    TaskProvider<NetBeansTask> setupNetbeansTask(TaskProvider<Jar> jarTaskProvider, Provider<String> moduleJarFilename) {
        return project.tasks.register(NETBEANS_TASK, NetBeansTask) {
            setDescription "Generates a NetBeans module directory."
            setGroup BasePlugin.BUILD_GROUP

            moduleBuildDir = nbmExtension.nbmModuleBuildDir
            inputModuleJarFile = jarTaskProvider.flatMap { it.archiveFile }
            classpath.setFrom providers.provider { getNetbeansClasspath() }
            classpathExtFolder = nbmExtension.classpathExtFolder
            outputModuleJarFileName = moduleJarFilename
            generateLastModified = nbmExtension.generateLastModifiedFile
            lastModifiedTimestampProvider = nbmExtension.lastModifiedTimestampProvider
            netbeansModuleStatusXml = objects.newInstance(NetbeansModuleStatusXml).tap {
                isAutoload = nbmExtension.autoload
                isEager = nbmExtension.eager
            }
            harnessConfiguration = nbmExtension.harnessConfiguration
        }
    }

    TaskProvider<NbmTask> setupNbmTask(TaskProvider<NetBeansTask> netbeansTaskProvider, Provider<String> moduleJarFilename) {
        TaskProvider<NbmTask> nbmTaskProvider = project.tasks.register(NBM_TASK, NbmTask) {
            setGroup BasePlugin.BUILD_GROUP

            nbmBuildDir = nbmExtension.nbmBuildDir
            outputFileName.convention nbmExtension.moduleName.map {
                it.replace('.', '-') + '.nbm'
            }
            moduleBuildDir = netbeansTaskProvider.flatMap { it.moduleBuildDir }
            it.moduleJarFileName = moduleJarFilename

            autoupdateModuleInfoXml = objects.newInstance(NetbeansAutoupdateModuleInfoXml).tap {
                licenseFile = nbmExtension.licenseFile
                moduleAuthor = nbmExtension.moduleAuthor
                homePage = nbmExtension.homePage
                distribution = nbmExtension.distribution
                needRestart = nbmExtension.needsRestart
                cluster = nbmExtension.cluster
            }
            keyStore = nbmExtension.keyStore
            harnessConfiguration = nbmExtension.harnessConfiguration
        }

        return nbmTaskProvider
    }

    void addRunTask(TaskProvider<NetBeansTask> netBeansTask,  String taskName, boolean debug) {
        project.tasks.register(taskName, Exec) {
            dependsOn netBeansTask
            doNotTrackState("Needs to re-run every time")

            Path buildPath = project.buildDir.toPath()
            Path testUserDir = buildPath.resolve(NetBeansTask.TEST_USER_DIR_NAME)
            if (project.hasProperty('netBeansExecutable')) {
                doFirst {
                    def confFile = testUserDir.resolve('etc').resolve('netbeans.conf')
                    Files.createDirectories(confFile.parent)
                    confFile.toFile().write "netbeans_extraclusters=\"${buildPath.resolve('module')}\""
                }

                workingDir project.buildDir

                List args = new LinkedList()
                args.addAll([project.netBeansExecutable, '--userdir', testUserDir])

                String debuggerPort = null;
                if (project.hasProperty('debuggerJpdaPort')) {
                    debuggerPort = project.debuggerJpdaPort
                }

                if (debuggerPort != null) {
                    args.add('-J-Xdebug')
                    args.add("-J-Xrunjdwp:transport=dt_socket,server=n,address=${debuggerPort}")
                } else if (debug) {
                    def nbmDebugPort = '5006'
                    if (project.hasProperty(nbmDebugPort)) {
                        nbmDebugPort = project.nbmDebugPort.trim()
                    }
                    args.add("-J-agentlib:jdwp=transport=dt_socket,server=y,address=${nbmDebugPort}")
                }
                commandLine args
            } else {
                doFirst {
                    throw new IllegalStateException('The property netBeansExecutable is not specified, you should define it in ~/.gradle/gradle.properties')
                }
            }
        }
    }

    FileCollection getNetbeansClasspath() {
        FileCollection runtimeClasspath = getJavaPluginRuntimeClasspath()
        return runtimeClasspath.minus(provideRuntimeConfiguration)
            .minus(implementationConfiguration)
            .minus(bundleConfiguration)
    }

    FileCollection getJavaPluginRuntimeClasspath() {
        project.getExtensions()
            .getByType(JavaPluginExtension.class)
            .getSourceSets()
            .getByName(SourceSet.MAIN_SOURCE_SET_NAME)
            .getRuntimeClasspath()
    }
}
