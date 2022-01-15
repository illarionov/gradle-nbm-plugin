package org.gradle.plugins.nbm;

import groovy.lang.Closure;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public final class NbmPluginExtension {
    // pattern copied from http://netbeans.org/ns/nb-module-project/3.xsd
    // (code-name-base) and add optional major version to patter
    static final Pattern MODULE_NAME_PATTERN = Pattern.compile(
        "\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*(?:[.]\\p{javaJavaIdentifierPart}+)*(/\\d+)?");

    private String moduleName;
    private String cluster;
    private String specificationVersion;
    private String implementationVersion;
    private boolean eager;
    private boolean autoload;
    private final NbmKeyStoreDef keyStore;
    private final Project project;
    private final List<String> requires;
    private String localizingBundle;
    private String moduleInstall;
    private final ModulePublicPackagesList publicPackages;
    private final ModuleFriendsList moduleFriends;
    private File licenseFile;
    private String moduleAuthor;
    private final Property<String> distribution;
    private String homePage;
    private Boolean needsRestart;
    private String layer;
    private String javaDependency;
    private final Property<Boolean> autoupdateShowInClient;
    private final Configuration harnessConfiguration;
    private String classpathExtFolder;
    private final String buildDate;

    public NbmPluginExtension(Project project) {
        Objects.requireNonNull(project, "project");
        this.project = project;

        this.harnessConfiguration = project.getConfigurations()
            .detachedConfiguration(project.getDependencies().create("org.codehaus.mojo:nbm-maven-harness:8.2"));

        this.moduleName = null;
        this.cluster = null;
        this.specificationVersion = null;
        this.implementationVersion = null;
        this.localizingBundle = null;
        this.moduleInstall = null;
        this.licenseFile = null;
        this.moduleAuthor = null;
        this.homePage = null;
        this.needsRestart = null;
        this.eager = false;
        this.autoload = false;
        this.publicPackages = new ModulePublicPackagesList(this.project);
        this.moduleFriends = new ModuleFriendsList(this.project);
        this.keyStore = new NbmKeyStoreDef();
        this.requires = new LinkedList<>();
        this.classpathExtFolder = null;
        this.autoupdateShowInClient = project.getObjects().property(Boolean.class);

        this.distribution = project.getObjects().property(String.class);
        distribution.convention(project.provider(() -> getModuleName().replace('.', '-') + ".nbm"));

        // Initializse default values
        this.buildDate = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date(System.currentTimeMillis()));
        requires("org.openide.modules.ModuleFormat1");
    }

    public String getBuildDate() {
        return buildDate;
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

    public Boolean getNeedsRestart() {
        return needsRestart;
    }

    public void setNeedsRestart(Boolean needsRestart) {
        this.needsRestart = needsRestart;
    }

    public String getHomePage() {
        return homePage;
    }

    public void setHomePage(String homePage) {
        this.homePage = homePage;
    }

    public Property<String> getDistribution() {
        return distribution;
    }

    public void setDistribution(String url) {
        distribution.set(url);
    }

    public void setDistributionUrl(Provider<? extends String> urlProvider) {
        distribution.set(urlProvider);
    }

    public String getModuleAuthor() {
        return moduleAuthor;
    }

    public void setModuleAuthor(String moduleAuthor) {
        this.moduleAuthor = moduleAuthor;
    }

    public File getLicenseFile() {
        return licenseFile;
    }

    public void setLicenseFile(Object licenseFile) {
        this.licenseFile = licenseFile != null ? project.file(licenseFile) : null;
    }

    public String getModuleInstall() {
        return moduleInstall;
    }

    public void setModuleInstall(String moduleInstall) {
        this.moduleInstall = moduleInstall;
    }

    public String getLocalizingBundle() {
        return localizingBundle;
    }

    public void setLocalizingBundle(String localizingBundle) {
        this.localizingBundle = localizingBundle;
    }

    public List<String> getRequires() {
        return requires;
    }

    public void setRequires(List<String> requires) {
        Objects.requireNonNull(requires, "requires");
        this.requires.clear();
        this.requires.addAll(requires);
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

    public String getModuleName() {
        if (moduleName == null) {
            return project.getName().replace('-', '.');
        }
        return moduleName;
    }

    public void setModuleName(String moduleName) {
        if (!MODULE_NAME_PATTERN.matcher(moduleName).matches()) {
            throw new InvalidUserDataException(
                "Illegal module friend name - '" + moduleName + "' (must match '" + MODULE_NAME_PATTERN + "'");
        }
        this.moduleName = moduleName;
    }

    public String getCluster() {
        return cluster;
    }

    public void setCluster(String cluster) {
        this.cluster = cluster;
    }

    public String getSpecificationVersion() {
        if (specificationVersion == null) {
            return EvaluateUtils.asString(project.getVersion());
        }
        return specificationVersion;
    }

    public void setSpecificationVersion(String specificationVersion) {
        this.specificationVersion = specificationVersion;
    }

    public String getImplementationVersion() {
        return implementationVersion;
    }

    public void setImplementationVersion(String implementationVersion) {
        this.implementationVersion = implementationVersion;
    }

    public boolean isEager() {
        return eager;
    }

    public void setEager(boolean eager) {
        this.eager = eager;
    }

    public boolean isAutoload() {
        return autoload;
    }

    public void setAutoload(boolean autoload) {
        this.autoload = autoload;
    }

    public String getLayer() {
        return layer;
    }

    public void setLayer(String layer) {
        this.layer = layer;
    }

    public String getJavaDependency() {
        return javaDependency;
    }

    public void setJavaDependency(String javaDependency) {
        this.javaDependency = javaDependency;
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

    public String getClasspathExtFolder() {
        return classpathExtFolder;
    }

    public void setClasspathExtFolder(String classpathExtFolder) {
        this.classpathExtFolder = classpathExtFolder;
    }
}
