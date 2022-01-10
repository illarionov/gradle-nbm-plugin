package org.gradle.plugins.nbm

import org.gradle.api.Plugin
import org.gradle.api.Project

public class NbmPlugin implements Plugin<Project> {
    public static final String PROVIDED_COMPILE_CONFIGURATION_NAME = "providedCompile";
    public static final String PROVIDED_RUNTIME_CONFIGURATION_NAME = "providedRuntime";
    public static final String IMPLEMENTATION_CONFIGURATION_NAME = "nbimplementation";
    public static final String BUNDLE_CONFIGURATION_NAME = "bundle";

    public static final String NBM_TASK = 'nbm'
    public static final String NETBEANS_TASK = 'netbeans'
    public static final String MANIFEST_TASK = 'generateModuleManifest'

    void apply(Project project) {
        project.apply plugin: 'java';
        project.logger.info "Registering deferred NBM plugin configuration..."

        NmbPluginTaskConfigurer configurer = project.objects.newInstance(NmbPluginTaskConfigurer)
        configurer.apply()
    }
}
