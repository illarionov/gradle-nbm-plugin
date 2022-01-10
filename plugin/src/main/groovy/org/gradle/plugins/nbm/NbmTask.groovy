package org.gradle.plugins.nbm

import org.apache.tools.ant.taskdefs.Taskdef
import org.apache.tools.ant.types.Path
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.internal.ConventionTask
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class NbmTask extends ConventionTask {

    private NetbeansAutoupdateModuleInfoXml autoupdateModuleInfoXml

    private NbmKeyStoreDef keyStore

    @OutputDirectory
    abstract DirectoryProperty getNbmBuildDir()

    @Input
    abstract Property<String> getOutputFileName()

    @InputFiles
    abstract DirectoryProperty getModuleBuildDir()

    @Input
    abstract Property<String> getModuleJarFileName()

    @Classpath
    abstract Property<Configuration> getHarnessConfiguration()

    @Nested
    NetbeansAutoupdateModuleInfoXml getAutoupdateModuleInfoXml() {
        return autoupdateModuleInfoXml;
    }

    void setAutoupdateModuleInfoXml(NetbeansAutoupdateModuleInfoXml moduleInfoXml) {
        this.autoupdateModuleInfoXml = moduleInfoXml;
    }

    @Nested
    NbmKeyStoreDef getKeyStore() {
        return keyStore
    }

    void setKeyStore(NbmKeyStoreDef keyStore) {
        this.keyStore = keyStore
    }

    @OutputFile
    Provider<RegularFile> getOutputFile() {
        return getNbmBuildDir().flatMap {
            return it.file(getOutputFileName())
        }
    }

    @TaskAction
    void generate() {
        def nbmFile = getOutputFile().get().asFile
        def nbmDir = getNbmBuildDir().get().asFile
        if (!nbmDir.isDirectory()) {
            nbmDir.mkdirs()
        }

        def makenbm = antBuilder().antProject.createTask("makenbm")
        makenbm.productDir = getModuleBuildDir().get().asFile
        makenbm.file = nbmFile
        makenbm.module = "modules" + File.separator + getModuleJarFileName().get()

        RegularFile licenseFile = autoupdateModuleInfoXml.licenseFile.getOrNull()
        if (licenseFile != null) {
            makenbm.createLicense().file = licenseFile.asFile
        }

        String moduleAuthor = autoupdateModuleInfoXml.moduleAuthor.getOrNull()
        if (moduleAuthor != null) {
            makenbm.moduleauthor = moduleAuthor
        }

        String homePage = autoupdateModuleInfoXml.homePage.getOrNull()
        if (homePage != null) {
            makenbm.homepage = homePage
        }

        String distribution = autoupdateModuleInfoXml.distribution.getOrNull()
        if (distribution != null) {
            makenbm.distribution = distribution
        }

        Boolean needsRestart = autoupdateModuleInfoXml.needRestart.getOrNull()
        if (needsRestart != null) {
            makenbm.needsrestart = needsRestart.toString()
        }

        def keyStoreFile = keyStore.keyStoreFile.getOrNull()
        if (keyStoreFile != null) {
            def signature = makenbm.createSignature()
            signature.keystore = keyStoreFile.asFile
            signature.alias = keyStore.username.getOrNull()
            signature.storepass = keyStore.password.getOrNull()
        }

        // The CreateNbmMojo class tests for "extra" (the default cluster)
        // and will not set the target cluster to that value.  We should do the
        // same.
        String cluster = autoupdateModuleInfoXml.cluster.getOrElse('extra')
        if (!cluster.equals("extra")) {
            makenbm.setTargetcluster(cluster)
        }

        makenbm.execute()
    }

    private AntBuilder antBuilder() {
        def antProject = ant.antProject
        ant.project.getBuildListeners().firstElement().setMessageOutputLevel(3)
        Taskdef taskdef = antProject.createTask("taskdef")
        taskdef.classname = "org.netbeans.nbbuild.MakeNBM"
        taskdef.name = "makenbm"
        taskdef.classpath = new Path(antProject, harnessConfiguration.get().asPath)
        taskdef.execute()
        return getAnt();
    }
}
