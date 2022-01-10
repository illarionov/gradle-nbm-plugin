package org.gradle.plugins.nbm

import org.apache.tools.ant.taskdefs.Taskdef
import org.apache.tools.ant.types.FileSet
import org.apache.tools.ant.types.Path
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.CopySpec
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.FileTreeElement
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.ConventionTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

import javax.inject.Inject

import java.util.jar.Attributes
import java.util.jar.JarFile

abstract class NetBeansTask extends ConventionTask {
    public static final String TEST_USER_DIR_NAME = 'testuserdir'

    private final FileSystemOperations fileOperations;

    private NetbeansModuleStatusXml netbeansModuleStatusXml;

    @OutputDirectory
    abstract DirectoryProperty getModuleBuildDir()

    @Input
    @Optional
    abstract Property<Boolean> getGenerateLastModified()

    @Internal
    abstract Property<Long> getLastModifiedTimestampProvider()

    @InputFile
    abstract RegularFileProperty getInputModuleJarFile()

    @Input
    abstract Property<String> getOutputModuleJarFileName()

    @Input
    @Optional
    abstract Property<String> getClasspathExtFolder()

    /**
     * Classpath to include in the module content.
     */
    @InputFiles
    @Optional
    abstract ConfigurableFileCollection getClasspath()

    @Classpath
    abstract Property<Configuration> getHarnessConfiguration()

    @Inject
    public NetBeansTask(FileSystemOperations fileOperations) {
        this.fileOperations = fileOperations
    }

    @Nested
    public NetbeansModuleStatusXml getNetbeansModuleStatusXml() {
        return netbeansModuleStatusXml
    }

    public setNetbeansModuleStatusXml(NetbeansModuleStatusXml netbeansModuleStatusXml) {
        this.netbeansModuleStatusXml = netbeansModuleStatusXml;
    }

    @Internal
    protected File getCacheDir() {
        File result = project.buildDir
        result = new File(result, TEST_USER_DIR_NAME)
        result = new File(result, 'var')
        result = new File(result, 'cache')
        return result
    }

    @TaskAction
    void generate() {
        def moduleDir = getModuleBuildDir().get().getAsFile()
        if (!moduleDir.isDirectory()) {
            moduleDir.mkdirs()
        }
        def timestamp = new File(moduleDir, ".lastModified")
        if (generateLastModified.getOrElse(true)) {
            timestamp.createNewFile()
            long newTimestamp = lastModifiedTimestampProvider.getOrNull() ?: System.currentTimeMillis();
            timestamp.setLastModified(newTimestamp)
        } else {
            timestamp.delete()
        }

        def modulesDir = new File(moduleDir, 'modules')

        def classpathExtFolder = classpathExtFolder.getOrNull()
        def modulesExtDir = new File(modulesDir, 'ext' + (classpathExtFolder ? "/$classpathExtFolder" : ""))

        fileOperations.delete {
            delete(getCacheDir())
        }

        def moduleJarName = getOutputModuleJarFileName().get()

        fileOperations.copy { CopySpec it ->
            it.from(inputModuleJarFile)
            it.into(modulesDir)
            it.rename('.*\\.jar', moduleJarName)
        }

        fileOperations.copy { CopySpec it ->
            it.from(classpath)
            it.into(modulesExtDir)
            it.exclude { FileTreeElement fte ->
                if (fte.directory) return true
                if (!fte.name.endsWith('jar')) return true

                JarFile jar = new JarFile(fte.file)
                def attrs = jar.manifest?.mainAttributes
                def attrValue = attrs?.getValue(new Attributes.Name('OpenIDE-Module'))
                attrValue != null
            }
        }

        AntBuilder antBuilder = antBuilder()
        def moduleXmlTask = antBuilder.antProject.createTask('module-xml')
        moduleXmlTask.xmldir = new File(moduleDir, 'config' + File.separator + 'Modules')
        FileSet moduleFileSet = new FileSet()
        moduleFileSet.setDir(moduleDir)
        moduleFileSet.setIncludes('modules' + File.separator + moduleJarName)

        if (netbeansModuleStatusXml.isAutoload.getOrElse(false)) {
            moduleXmlTask.addAutoload(moduleFileSet)
        } else if (netbeansModuleStatusXml.isEager.getOrElse(false)) {
            moduleXmlTask.addEager(moduleFileSet)
        } else {
            moduleXmlTask.addEnabled(moduleFileSet)
        }

        moduleXmlTask.execute()

        def nbTask = antBuilder.antProject.createTask('genlist')
        nbTask.outputfiledir = moduleDir
        nbTask.module = 'modules' + File.separator + moduleJarName
        FileSet fs = nbTask.createFileSet()
        fs.dir = moduleDir
        fs.setIncludes('**')
        nbTask.execute()
    }

    private AntBuilder antBuilder() {
        def antProject = ant.antProject
        ant.project.getBuildListeners().firstElement().setMessageOutputLevel(3)
        Taskdef taskdef = antProject.createTask("taskdef")
        taskdef.classname = "org.netbeans.nbbuild.MakeListOfNBM"
        taskdef.name = "genlist"
        taskdef.classpath = new Path(antProject, getHarnessConfiguration().get().asPath)
        taskdef.execute()
        Taskdef taskdef2 = antProject.createTask("taskdef")
        taskdef2.classname = "org.netbeans.nbbuild.CreateModuleXML"
        taskdef2.name = "module-xml"
        taskdef2.classpath = new Path(antProject, getHarnessConfiguration().get().asPath)
        taskdef2.execute()
        return getAnt();
    }
}
