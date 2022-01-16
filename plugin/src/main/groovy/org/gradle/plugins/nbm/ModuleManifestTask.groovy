package org.gradle.plugins.nbm

import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.internal.ConventionTask
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

import java.nio.file.Files
import java.util.jar.Attributes
import java.util.jar.JarFile
import java.util.jar.Manifest

class ModuleManifestTask extends ConventionTask {
    @OutputFile
    File generatedManifestFile

    public ModuleManifestTask() {
        outputs.upToDateWhen { checkUpToDate() }
    }

    private NbmPluginExtension netbeansExt() {
        project.extensions.nbm
    }

    public boolean checkUpToDate() {
        byte[] actualBytes = tryGetCurrentGeneratedContent()
        if (actualBytes == null) {
            return false
        }

        Manifest oldMf = new Manifest(new ByteArrayInputStream(actualBytes));
        Manifest mf = createManifest()

        Attributes oldAttribs = oldMf.getMainAttributes()
        Attributes mfAttribs = mf.getMainAttributes()

        // Remove dynamic (time) related content
        // TODO Ensure values are conform to SimpleDateFormat of NbmPluginExtension.buildDate
        def attrImplVersion = new Attributes.Name('OpenIDE-Module-Implementation-Version')
        def attrBuildVersion = new Attributes.Name('OpenIDE-Module-Build-Version')
        if (oldAttribs.containsKey(attrBuildVersion) && mfAttribs.containsKey(attrBuildVersion)) {
            logger.debug "UP-TO-DATE check - exclude dynamic manifest values: build version found ->  assume dynamic build version (time stamp) -> include implementation version in check (exclude build version)"
            oldAttribs.remove(attrBuildVersion)
            mfAttribs.remove(attrBuildVersion)
        } else if (!oldAttribs.containsKey(attrBuildVersion) && !mfAttribs.containsKey(attrBuildVersion)) {
            logger.debug "UP-TO-DATE check - exclude dynamic manifest values: no build version found -> assume dynamic implementation version (time stamp) -> exclude implementation version in check"

            oldAttribs.remove(attrImplVersion)
            mfAttribs.remove(attrImplVersion)
        }

        return oldMf == mf
    }

    private byte[] tryGetCurrentGeneratedContent() {
        def manifestFile = getGeneratedManifestFile().toPath()
        if (!Files.isRegularFile(manifestFile)) {
            return null
        }

        try {
            return Files.readAllBytes(manifestFile)
        } catch (IOException ex) {
            return null;
        }
    }

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

        def requires = netbeansExt().requires;
        if (!requires.isEmpty()) {
            result.put('OpenIDE-Module-Requires', requires.join(', '))
        }

        def localizingBundle = netbeansExt().localizingBundle
        if (localizingBundle) {
            result.put('OpenIDE-Module-Localizing-Bundle', localizingBundle)
        }

        result.put('OpenIDE-Module', netbeansExt().moduleName)

        String buildVersion = netbeansExt().buildVersion.get()
        String implVersion = netbeansExt().implementationVersion.getOrNull()
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

        result.put('OpenIDE-Module-Specification-Version', netbeansExt().specificationVersion.get())

        SortedSet<String> publicPackages = netbeansExt().publicPackages.entries
        if (!publicPackages.isEmpty()) {
            result.put('OpenIDE-Module-Public-Packages', publicPackages.join(', '))
        } else {
            result.put('OpenIDE-Module-Public-Packages', '-')
        }

        SortedSet<String> moduleFriends = netbeansExt().moduleFriends.entries
        if (!moduleFriends.isEmpty()) {
            if (publicPackages.isEmpty()) {
                throw new InvalidUserDataException("Module friends can't be specified without defined public packages")
            }
            result.put('OpenIDE-Module-Friends', moduleFriends.join(', '))
        }

        def layer = netbeansExt().layer
        if (layer) {
            result.put('OpenIDE-Module-Layer', layer)
        }

        def javaDependency = netbeansExt().javaDependency
        if (javaDependency) {
            result.put('OpenIDE-Module-Java-Dependencies', javaDependency)
        }

        def autoupdateShowInClient = netbeansExt().autoupdateShowInClient.getOrNull()
        if (autoupdateShowInClient != null) {
            result.put('AutoUpdate-Show-In-Client', autoupdateShowInClient.toString())
        }

        def moduleInstall = netbeansExt().moduleInstall
        if (moduleInstall) {
            result.put('OpenIDE-Module-Install', moduleInstall.replace('.', '/') + '.class')
        }

        return result
    }

    private Map<String, String> computeModuleDependencies() {
        Map<String, String> moduleDeps = new TreeMap()

        def mainSourceSet = project.sourceSets.main
        def compileConfig = project.configurations.findByName(mainSourceSet.runtimeClasspathConfigurationName).resolvedConfiguration

        Set<ResolvedArtifact> implArtifacts = new HashSet<>()
        project.configurations.nbimplementation.resolvedConfiguration.firstLevelModuleDependencies.each { ResolvedDependency it ->
            implArtifacts.addAll(it.moduleArtifacts)
        }

        Set<ResolvedArtifact> bundleArtifacts = new HashSet<>()
        project.configurations.bundle.resolvedConfiguration.firstLevelModuleDependencies.each { ResolvedDependency it ->
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
        def manifestFile = getGeneratedManifestFile()
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
        FileCollection classpath = project.tasks.findByPath('netbeans').classpath
        String classpathExtFolder = netbeansExt().classpathExtFolder
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
