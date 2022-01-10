package org.gradle.plugins.nbm

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

interface NetbeansAutoupdateModuleInfoXml {

    @Input
    @Optional
    RegularFileProperty getLicenseFile()

    @Input
    @Optional
    Property<String> getModuleAuthor()

    @Input
    @Optional
    Property<String> getHomePage()

    @Input
    @Optional
    Property<String> getDistribution()

    @Input
    @Optional
    Property<Boolean> getNeedRestart()

    @Input
    @Optional
    Property<String> getCluster()
}
