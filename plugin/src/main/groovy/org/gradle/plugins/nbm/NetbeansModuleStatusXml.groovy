package org.gradle.plugins.nbm

import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

interface NetbeansModuleStatusXml {

    @Input
    @Optional
    Property<Boolean> getIsAutoload()

    @Input
    @Optional
    Property<Boolean> getIsEager()
}
