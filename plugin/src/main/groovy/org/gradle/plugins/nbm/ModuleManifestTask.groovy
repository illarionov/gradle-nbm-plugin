package org.gradle.plugins.nbm

import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.internal.ConventionTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

import java.util.jar.Attributes
import java.util.jar.JarFile
import java.util.jar.Manifest

import static java.util.Collections.emptySet

abstract class ModuleManifestTask extends ConventionTask {

    private ModuleManifestConfig moduleManifestConfig

    @OutputFile
    abstract Property<File> getGeneratedManifestFile()

    @Nested
    ModuleManifestConfig getManifestConfig() {
        return moduleManifestConfig
    }

    void setModuleManifestConfig(ModuleManifestConfig config) {
        this.moduleManifestConfig = config
    }

    @Input
    @Optional
    abstract Property<String> getNetbeansClasspathExtFolder()

    @Classpath
    abstract ConfigurableFileCollection getNetbeansClasspath()

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract Property<Configuration> getRuntimeConfiguration()

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract Property<Configuration> getNbImplementationConfiguration()

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract Property<Configuration> getBundleConfiguration()

    private Map<String, String> getManifestEntries() {
        Map<String, String> result = new LinkedHashMap<>()

        result.put('Manifest-Version', '1.0')

        def classpath = computeClasspath()
        if (classpath != null && !classpath.isEmpty()) {
            result.put('Class-Path', classpath)
        }

        def moduleDeps = computeModuleDependencies()
        if (!moduleDeps.isEmpty())
            result.put(
                'OpenIDE-Module-Module-Dependencies',
                moduleDeps.entrySet().collect { it.key + it.value }.join(', ')
            )

        result.put('Created-By', 'Gradle NBM plugin')

        def requires = manifestConfig.requires.getOrElse([]);
        if (!requires.isEmpty()) {
            result.put('OpenIDE-Module-Requires', requires.join(', '))
        }

        def localizingBundle = manifestConfig.localizedBundle.getOrNull()
        if (localizingBundle) {
            result.put('OpenIDE-Module-Localizing-Bundle', localizingBundle)
        }

        result.put('OpenIDE-Module', manifestConfig.moduleName.get())

        String buildVersion = manifestConfig.buildVersion.getOrNull()
        String implVersion = manifestConfig.implementationVersion.getOrNull()
        if (implVersion == null) {
            implVersion = buildVersion
            buildVersion = null
        }

        if (implVersion != null && !implVersion.isBlank()) {
            result.put('OpenIDE-Module-Implementation-Version', implVersion)
        }

        if (buildVersion != null && !buildVersion.isBlank()) {
            result.put('OpenIDE-Module-Build-Version', buildVersion)
        }

        result.put('OpenIDE-Module-Specification-Version', manifestConfig.specificationVersion.get())

        Set<String> publicPackages = manifestConfig.publicPackages.getOrElse(emptySet())
        if (!publicPackages.isEmpty()) {
            result.put('OpenIDE-Module-Public-Packages', publicPackages.join(', '))
        } else {
            result.put('OpenIDE-Module-Public-Packages', '-')
        }

        Set<String> moduleFriends = manifestConfig.moduleFriends.getOrElse(emptySet())
        if (!moduleFriends.isEmpty()) {
            if (publicPackages.isEmpty()) {
                throw new InvalidUserDataException("Module friends can't be specified without defined public packages")
            }
            result.put('OpenIDE-Module-Friends', moduleFriends.join(', '))
        }

        def layer = manifestConfig.layer.getOrNull()
        if (layer) {
            result.put('OpenIDE-Module-Layer', layer)
        }

        def javaDependency = manifestConfig.javaDependency.getOrNull()
        if (javaDependency) {
            result.put('OpenIDE-Module-Java-Dependencies', javaDependency)
        }

        def autoupdateShowInClient = manifestConfig.autoupdateShowInClient.getOrNull()
        if (autoupdateShowInClient != null) {
            result.put('AutoUpdate-Show-In-Client', autoupdateShowInClient.toString())
        }

        def moduleInstall = manifestConfig.moduleInstall.getOrNull()
        if (moduleInstall) {
            result.put('OpenIDE-Module-Install', moduleInstall.replace('.', '/') + '.class')
        }

        return result
    }

    private Map<String, String> computeModuleDependencies() {
        Map<String, String> moduleDeps = new TreeMap()

        def compileConfig = getRuntimeConfiguration().get().resolvedConfiguration

        Set<ResolvedArtifact> implArtifacts = new HashSet<>()
        getNbImplementationConfiguration().get().resolvedConfiguration.firstLevelModuleDependencies.each { ResolvedDependency it ->
            implArtifacts.addAll(it.moduleArtifacts)
        }

        Set<ResolvedArtifact> bundleArtifacts = new HashSet<>()
        getBundleConfiguration().get().resolvedConfiguration.firstLevelModuleDependencies.each { ResolvedDependency it ->
            bundleArtifacts.addAll(it.moduleArtifacts)
        }

        compileConfig.firstLevelModuleDependencies.each { ResolvedDependency it ->
            // println 'module ' + it.name + ', ' + it.id.id
            it.moduleArtifacts.each { a ->
                // println '  artifact ' + a + ' file ' + a.file
                if (a.file?.exists() && 'jar' == a.extension) {
                    JarFile jar = new JarFile(a.file)
                    def attrs = jar.manifest?.mainAttributes
                    def bundleName = attrs?.getValue(new Attributes.Name('Bundle-SymbolicName'))
                    if (bundleName && bundleArtifacts.contains(a)) {
                        moduleDeps.put(bundleName.split(';').first(), '')
                    } else {
                        def moduleName = attrs?.getValue(new Attributes.Name('OpenIDE-Module'))
                        def moduleVersion = attrs?.getValue(new Attributes.Name('OpenIDE-Module-Specification-Version'))
                        def implVersion = attrs?.getValue(new Attributes.Name('OpenIDE-Module-Implementation-Version'))
                        if (moduleName && moduleVersion) {
                            if (implArtifacts.contains(a))
                                moduleDeps.put(moduleName, " = $implVersion")
                            else
                                moduleDeps.put(moduleName, " > $moduleVersion")
                        }
                    }
                }
            }
        }
        moduleDeps
    }

    private Manifest createManifest() {
        def manifest = new Manifest()
        def mainAttributes = manifest.mainAttributes

        getManifestEntries().each { key, value ->
            logger.debug('add manifest entry {}: {}/{}', key, value, value == null)
            mainAttributes.put(new Attributes.Name(key), value)
        }
        return manifest
    }

    @TaskAction
    void generate() {
        def manifestFile = getGeneratedManifestFile().get()
        logger.info "Generating NetBeans module manifest $manifestFile"

        def os = new FileOutputStream(manifestFile)
        try {
            createManifest().write(os)
        } finally {
            os.close()
        }
    }

    private String computeClasspath() {
        def jarNames = [] as Set
        FileCollection classpath = getNetbeansClasspath()
        String classpathExtFolder = getNetbeansClasspathExtFolder().getOrNull()
        classpath.asFileTree.visit { FileVisitDetails fvd ->
            if (fvd.directory) return
            if (!fvd.name.endsWith('jar')) return

            JarFile jar = new JarFile(fvd.file)
            def attrs = jar.manifest?.mainAttributes
            def attrValue = attrs?.getValue(new Attributes.Name('OpenIDE-Module'))
            if (attrValue != null) return

            // JAR but not NetBeans module
            jarNames += 'ext/' + (classpathExtFolder ? "$classpathExtFolder/" : "") + fvd.name
        }
        jarNames.join(' ')
    }
}
