package org.gradle.plugins.nbm;

import groovy.lang.Closure;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;

import java.io.File;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import static java.util.Collections.emptySet;

public class NbmPluginExtension {
    // pattern copied from http://netbeans.org/ns/nb-module-project/3.xsd
    // (code-name-base) and add optional major version to patter
    static final Pattern MODULE_NAME_PATTERN = Pattern.compile(
        "\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*(?:[.]\\p{javaJavaIdentifierPart}+)*(/\\d+)?");

    private final Property<String> moduleName;
    private final Property<String> cluster;
    private final Property<String> specificationVersion;
    private final Property<String> implementationVersion;
    private final Property<String> buildVersion;
    private final Property<Boolean> eager;
    private final Property<Boolean> autoload;
    private final NbmKeyStoreDef keyStore;
    private final Project project;
    private final ListProperty<String> requires;
    private final Property<String> localizingBundle;
    private final Property<String> moduleInstall;
    private final ModulePublicPackagesList publicPackages;
    private final ModuleFriendsList moduleFriends;
    private final RegularFileProperty licenseFile;
    private final Property<String> moduleAuthor;
    private final Property<String> distribution;
    private final Property<String> homePage;
    private final Property<Boolean> needsRestart;
    private final Property<String> layer;
    private final Property<String> javaDependency;
    private final Property<Boolean> autoupdateShowInClient;
    private final Configuration harnessConfiguration;
    private final Property<String> classpathExtFolder;
    private final Property<Boolean> generateLastModifiedFile;
    private final Provider<Long> lastModifiedTimestampProvider;

    private final Property<String> archiveFileName;
    private final DirectoryProperty nbmBuildDir;
    private final DirectoryProperty nbmModuleBuildDir;
    private final RegularFileProperty generatedManifestFile;

    private final Clock clock;
    private Instant buildTimestamp;

    public NbmPluginExtension(Project project, Clock clock) {
        Objects.requireNonNull(project, "project");
        this.project = project;
        this.clock = clock;
        final ObjectFactory objects = project.getObjects();
        final ProviderFactory providers = project.getProviders();

        this.harnessConfiguration = project.getConfigurations()
            .detachedConfiguration(project.getDependencies().create("org.codehaus.mojo:nbm-maven-harness:8.2"));

        this.moduleName = objects.property(String.class)
            .convention(providers.provider(() -> project.getName().replace('-', '.')));
        this.cluster = objects.property(String.class).convention("extra");

        this.specificationVersion = objects.property(String.class).convention(providers.provider(() -> {
            return EvaluateUtils.asString(project.getVersion());
        }));
        this.implementationVersion = objects.property(String.class);
        this.buildVersion = objects.property(String.class).convention(providers.provider(() -> {
            return DateTimeFormatter.ofPattern("yyyyMMddHHmss", Locale.ROOT).withZone(ZoneOffset.UTC)
                .format(getBuildTimestamp());
        }));

        this.localizingBundle = objects.property(String.class);
        this.moduleInstall = objects.property(String.class);
        this.licenseFile = objects.fileProperty();
        this.moduleAuthor = objects.property(String.class);
        this.homePage = objects.property(String.class);
        this.needsRestart = objects.property(Boolean.class);
        this.layer = objects.property(String.class);
        this.javaDependency = objects.property(String.class);
        this.eager = objects.property(Boolean.class).convention(false);
        this.autoload = objects.property(Boolean.class).convention(false);
        this.publicPackages = new ModulePublicPackagesList(this.project);
        this.moduleFriends = new ModuleFriendsList(this.project);
        this.keyStore = objects.newInstance(NbmKeyStoreDef.class);
        this.requires = objects.listProperty(String.class).convention(emptySet());
        this.classpathExtFolder = objects.property(String.class);
        this.autoupdateShowInClient = objects.property(Boolean.class);

        this.archiveFileName = objects.property(String.class).convention(getModuleName().map(name -> {
            return name.replace('.', '-') + ".nbm";
        }));

        this.distribution = objects.property(String.class);
        distribution.convention(archiveFileName);

        this.generateLastModifiedFile = objects.property(Boolean.class).convention(true);
        this.lastModifiedTimestampProvider = providers.provider(() -> getBuildTimestamp().toEpochMilli());

        this.nbmBuildDir = objects.directoryProperty().convention(project.getLayout().getBuildDirectory().dir("nbm"));
        this.nbmModuleBuildDir = objects.directoryProperty()
            .convention(project.getLayout().getBuildDirectory().dir("module"));

        this.generatedManifestFile = objects.fileProperty()
            .convention(project.getLayout().getBuildDirectory().file("generated-manifest.mf"));

        requires("org.openide.modules.ModuleFormat1");
    }

    public Provider<String> getSpecificationVersion() {
        return specificationVersion;
    }

    public void setSpecificationVersion(String specificationVersion) {
        this.specificationVersion.set(specificationVersion);
    }

    public void setSpecificationVersion(Provider<String> specificationVersionProvider) {
        this.specificationVersion.set(specificationVersionProvider);
    }

    public Provider<String> getImplementationVersion() {
        return implementationVersion;
    }

    public void setImplementationVersion(String implementationVersion) {
        this.implementationVersion.set(implementationVersion);
    }

    public void setImplementationVersion(Provider<String> implementationVersionProvider) {
        this.implementationVersion.set(implementationVersionProvider);
    }

    public Provider<String> getBuildVersion() {
        return buildVersion;
    }

    public void setBuildVersion(Provider<String> buildVersionProvider) {
        this.buildVersion.set(buildVersionProvider);
    }

    public void setBuildVersion(String buildVersion) {
        this.buildVersion.set(buildVersion);
    }

    @Deprecated
    public ModulePublicPackagesList getFriendPackages() {
        project.getLogger().error("'nbm' plugin: Use of 'friendPackages' is deprecated use 'publicPackages' instead!");
        return getPublicPackages();
    }

    public ModulePublicPackagesList getPublicPackages() {
        return publicPackages;
    }

    @Deprecated
    public void friendPackages(Closure<ModulePublicPackagesList> configBlock) {
        project.getLogger().error("'nbm' plugin: Use of 'friendPackages' is deprecated use 'publicPackages' instead!");
        publicPackages(configBlock);
    }

    public void publicPackages(Closure<ModulePublicPackagesList> configBlock) {
        configBlock.setResolveStrategy(Closure.DELEGATE_FIRST);
        configBlock.setDelegate(publicPackages);
        configBlock.call(publicPackages);
    }

    public ModuleFriendsList getModuleFriends() {
        return moduleFriends;
    }

    public void moduleFriends(Closure<ModuleFriendsList> configBlock) {
        configBlock.setResolveStrategy(Closure.DELEGATE_FIRST);
        configBlock.setDelegate(moduleFriends);
        configBlock.call(moduleFriends);
    }

    public Configuration getHarnessConfiguration() {
        return harnessConfiguration;
    }

    public Provider<Boolean> getNeedsRestart() {
        return needsRestart;
    }

    public void setNeedsRestart(Boolean needsRestart) {
        this.needsRestart.set(needsRestart);
    }

    public void setNeedsRestart(Provider<? extends Boolean> needsRestartProvider) {
        this.needsRestart.set(needsRestartProvider);
    }

    public Provider<String> getHomePage() {
        return homePage;
    }

    public void setHomePage(String homePage) {
        this.homePage.set(homePage);
    }

    public void setHomePage(Provider<String> homePageProvider) {
        this.homePage.set(homePageProvider);
    }

    public Provider<String> getDistribution() {
        return distribution;
    }

    public void setDistribution(String url) {
        distribution.set(url);
    }

    public void setDistributionUrl(Provider<String> urlProvider) {
        distribution.set(urlProvider);
    }

    public Provider<String> getModuleAuthor() {
        return moduleAuthor;
    }

    public void setModuleAuthor(String moduleAuthor) {
        this.moduleAuthor.set(moduleAuthor);
    }

    public void setModuleAuthor(Provider<String> moduleAuthorProvider) {
        this.moduleAuthor.set(moduleAuthorProvider);
    }

    public Provider<RegularFile> getLicenseFile() {
        return licenseFile;
    }

    public void setLicenseFile(File licenseFile) {
        this.licenseFile.set(licenseFile);
    }

    public void setLicenseFile(RegularFile licenseFile) {
        this.licenseFile.set(licenseFile);
    }

    public void setLicenseFile(Provider<? extends RegularFile> licenseFileProvider) {
        this.licenseFile.set(licenseFileProvider);
    }

    public void setLicenseFile(Object licenseFile) {
        this.licenseFile.set(project.file(licenseFile));
    }

    public Provider<String> getModuleInstall() {
        return moduleInstall;
    }

    public void setModuleInstall(String moduleInstall) {
        this.moduleInstall.set(moduleInstall);
    }

    public void setModuleInstall(Provider<String> moduleInstall) {
        this.moduleInstall.set(moduleInstall);
    }

    public Provider<String> getLocalizingBundle() {
        return localizingBundle;
    }

    public void setLocalizingBundle(String localizingBundle) {
        this.localizingBundle.set(localizingBundle);
    }

    public void setLocalizingBundle(Provider<String> localizingBundleProvider) {
        this.localizingBundle.set(localizingBundleProvider);
    }

    public Provider<List<String>> getRequires() {
        return requires;
    }

    public void setRequires(List<String> requires) {
        Objects.requireNonNull(requires, "requires");
        this.requires.set(requires);
    }

    public void setRequires(Provider<? extends List<String>> requires) {
        Objects.requireNonNull(requires, "requires");
        this.requires.set(requires);
    }

    public void requires(String dependency) {
        requires.add(dependency);
    }

    public void keyStore(Closure<NbmKeyStoreDef> configBlock) {
        configBlock.setResolveStrategy(Closure.DELEGATE_FIRST);
        configBlock.setDelegate(keyStore);
        configBlock.call(keyStore);
    }

    public NbmKeyStoreDef getKeyStore() {
        return keyStore;
    }

    public Provider<String> getModuleName() {
        return moduleName.map(name -> {
            if (!MODULE_NAME_PATTERN.matcher(name).matches()) {
                throw new InvalidUserDataException(
                    "Illegal module friend name - '" + name + "' (must match '" + MODULE_NAME_PATTERN + "'");
            }
            return name;
        });
    }

    public void setModuleName(String moduleName) {
        this.moduleName.set(moduleName);
    }

    public void setModuleName(Provider<String> moduleNameProvider) {
        this.moduleName.set(moduleNameProvider);
    }

    public Provider<String> getCluster() {
        return cluster;
    }

    public void setCluster(String cluster) {
        this.cluster.set(cluster);
    }

    public void setCluster(Provider<String> clusterProvider) {
        this.cluster.set(clusterProvider);
    }

    public Provider<Boolean> getEager() {
        return eager;
    }

    public void setEager(boolean eager) {
        this.eager.set(eager);
    }

    public void setEager(String eager) {
        this.eager.set(Boolean.valueOf(eager));
    }

    public void setEager(Provider<? extends Boolean> eagerProvider) {
        this.eager.set(eagerProvider);
    }

    public Provider<Boolean> getAutoload() {
        return autoload;
    }

    public void setAutoload(boolean autoload) {
        this.autoload.set(autoload);
    }

    public void setAutoload(String autoload) {
        this.autoload.set(Boolean.valueOf(autoload));
    }

    public void setAutoload(Provider<? extends Boolean> autoloadProvider) {
        this.autoload.set(autoloadProvider);
    }

    public Provider<String> getLayer() {
        return layer;
    }

    public void setLayer(String layer) {
        this.layer.set(layer);
    }

    public void setLayer(Provider<String> layerProvider) {
        this.layer.set(layerProvider);
    }

    public Provider<String> getJavaDependency() {
        return javaDependency;
    }

    public void setJavaDependency(String javaDependency) {
        this.javaDependency.set(javaDependency);
    }

    public void setJavaDependency(Provider<String> javaDependencyProvider) {
        this.javaDependency.set(javaDependencyProvider);
    }

    public Provider<Boolean> getAutoupdateShowInClient() {
        return autoupdateShowInClient;
    }

    public void setAutoupdateShowInClient(boolean autoupdateShowInClient) {
        this.autoupdateShowInClient.set(autoupdateShowInClient);
    }

    public void setAutoupdateShowInClient(Provider<? extends Boolean> autoupdateShowInClientProvider) {
        this.autoupdateShowInClient.set(autoupdateShowInClientProvider);
    }

    public Provider<String> getClasspathExtFolder() {
        return classpathExtFolder;
    }

    public void setClasspathExtFolder(String classpathExtFolder) {
        this.classpathExtFolder.set(classpathExtFolder);
    }

    public void setClasspathExtFolder(Provider<String> classpathExtFolder) {
        this.classpathExtFolder.set(classpathExtFolder);
    }

    public Provider<Boolean> getGenerateLastModifiedFile() {
        return generateLastModifiedFile;
    }

    public void setGenerateLastModifiedFile(boolean generateLastModified) {
        this.generateLastModifiedFile.set(generateLastModified);
    }

    public void setGenerateLastModifiedFile(Provider<Boolean> generateLastModifiedProvider) {
        this.generateLastModifiedFile.set(generateLastModifiedProvider);
    }

    Provider<Long> getLastModifiedTimestampProvider() {
        return project.getProviders().zip(generateLastModifiedFile, lastModifiedTimestampProvider,
            (enabled, timestamp) -> enabled ? timestamp : 0);
    }

    public Provider<String> getArchiveFileName() {
        return archiveFileName;
    }

    public void setArchiveFileName(String archiveFileName) {
        this.archiveFileName.set(archiveFileName);
    }

    public void setArchiveFileName(Provider<String> archiveFileNameProvider) {
        this.archiveFileName.set(archiveFileNameProvider);
    }

    public Provider<Directory> getNbmBuildDir() {
        return nbmBuildDir;
    }

    public void setNbmBuildDir(File directory) {
        nbmBuildDir.set(directory);
    }

    public void setNbmBuildDir(Directory directory) {
        nbmBuildDir.set(directory);
    }

    public void setNbmBuildDir(Provider<? extends Directory> directoryProvider) {
        nbmBuildDir.set(directoryProvider);
    }

    public Provider<Directory> getNbmModuleBuildDir() {
        return nbmModuleBuildDir;
    }

    public void setNbmModuleBuildDir(File directory) {
        nbmModuleBuildDir.set(directory);
    }

    public void setNbmModuleBuildDir(Directory directory) {
        nbmModuleBuildDir.set(directory);
    }

    public void setNbmModuleBuildDir(Provider<? extends Directory> directoryProvider) {
        nbmModuleBuildDir.set(directoryProvider);
    }

    public Provider<RegularFile> getGeneratedManifestFile() {
        return generatedManifestFile;
    }

    public void setGeneratedManifestFile(File file) {
        generatedManifestFile.set(file);
    }

    public void setGeneratedManifestFile(RegularFile file) {
        generatedManifestFile.set(file);
    }

    public void setGeneratedManifestFile(Provider<? extends RegularFile> file) {
        generatedManifestFile.set(file);
    }

    private synchronized Instant getBuildTimestamp() {
        if (buildTimestamp == null) {
            buildTimestamp = clock.instant();
        }
        return buildTimestamp;
    }
}
