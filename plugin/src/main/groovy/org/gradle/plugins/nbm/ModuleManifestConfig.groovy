package org.gradle.plugins.nbm

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional

interface ModuleManifestConfig {

    @Input
    Property<String> getModuleName()

    @Input
    @Optional
    ListProperty<String> getRequires()

    @Input
    @Optional
    Property<String> getLocalizedBundle()

    @Internal
    Property<String> getBuildVersion()

    @Input
    @Optional
    Property<String> getImplementationVersion()

    @Input
    @Optional
    Property<String> getSpecificationVersion()

    @Input
    @Optional
    SetProperty<String> getPublicPackages()

    @Input
    @Optional
    SetProperty<String> getModuleFriends()

    @Input
    @Optional
    Property<String> getLayer()

    @Input
    @Optional
    Property<String> getJavaDependency()

    @Input
    @Optional
    Property<Boolean> getAutoupdateShowInClient()

    @Input
    @Optional
    Property<String> getModuleInstall()
}
