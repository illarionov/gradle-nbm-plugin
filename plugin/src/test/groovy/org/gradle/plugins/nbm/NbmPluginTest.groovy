package org.gradle.plugins.nbm

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.internal.artifacts.configurations.Configurations
import org.gradle.api.plugins.JavaPlugin
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.empty
import static org.hamcrest.Matchers.equalTo
import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertNotNull
import static org.junit.jupiter.api.Assertions.assertNull
import static org.junit.jupiter.api.Assertions.assertTrue

public class NbmPluginTest {

    // nbm plugin adds nbm task to project when JavaPlugin already applied
    @Test
    public void checkProjectTask() {
        Project project = ProjectBuilder.builder().build()
        project.project.plugins.apply(JavaPlugin)
        project.project.plugins.apply(NbmPlugin)

        def nbmTask = project.tasks.nbm
        def netbeansTask = project.tasks.netbeans

        assertNotNull(nbmTask)

        def nbmTasksDeps = nbmTask.getTaskDependencies().getDependencies(nbmTask)
        def netbeansTaskDeps = netbeansTask.getTaskDependencies().getDependencies(netbeansTask)

        // assertTrue(task instanceof NbmTask)
        assertTrue(nbmTasksDeps.contains(netbeansTask))

        assertTrue(netbeansTaskDeps.contains(project.tasks.jar))
    }

    @Test
    public void createsConfigurations() {
        Project project = ProjectBuilder.builder().build()
        project.project.plugins.apply(JavaPlugin)
        project.project.plugins.apply(NbmPlugin)

        def configuration = project.configurations.getByName(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME)
        assertThat(Configurations.getNames(configuration.extendsFrom),
            equalTo(Set.of(NbmPlugin.PROVIDED_COMPILE_CONFIGURATION_NAME, NbmPlugin.IMPLEMENTATION_CONFIGURATION_NAME, NbmPlugin.BUNDLE_CONFIGURATION_NAME)))
        assertFalse(configuration.visible)
        assertTrue(configuration.transitive)

        configuration = project.configurations.getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME)
        assertThat(Configurations.getNames(configuration.extendsFrom), equalTo(Set.of(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME, JavaPlugin.RUNTIME_ONLY_CONFIGURATION_NAME, NbmPlugin.PROVIDED_RUNTIME_CONFIGURATION_NAME)))
        assertFalse(configuration.visible)
        assertTrue(configuration.transitive)

        configuration = project.configurations.getByName(NbmPlugin.PROVIDED_COMPILE_CONFIGURATION_NAME)
        assertThat(Configurations.getNames(configuration.extendsFrom), empty())
        assertFalse(configuration.visible)
        assertTrue(configuration.transitive)

        configuration = project.configurations.getByName(NbmPlugin.PROVIDED_RUNTIME_CONFIGURATION_NAME)
        assertThat(Configurations.getNames(configuration.extendsFrom), equalTo(Set.of(NbmPlugin.PROVIDED_COMPILE_CONFIGURATION_NAME)))
        assertFalse(configuration.visible)
        assertTrue(configuration.transitive)
    }

    // nbm plugin adds task to generate manifest used by JAR
    @Test
    public void checkManifest() {
        Project project = ProjectBuilder.builder().build()
        project.project.plugins.apply(JavaPlugin)
        project.project.plugins.apply(NbmPlugin)

        Task jarTask = project.tasks.find { jarTask -> jarTask.name == 'jar' }
        assertNotNull(jarTask)
        def manifestTasks = project.getTasks().withType(ModuleManifestTask)
        def jarTasksDeps = jarTask.getTaskDependencies().getDependencies(jarTask)

        assertTrue jarTasksDeps.contains(manifestTasks.iterator().next())
    }

    // nbm plugin hooks directories for merged properties
    @Test
    public void checkPluinDirectoryWithMergedProperties() {
        Project project = ProjectBuilder.builder().build()
        project.project.plugins.apply(JavaPlugin)
        project.project.plugins.apply(NbmPlugin)
        assertNotNull(project.project.sourceSets.main.output)
        assertTrue(project.tasks.getByName('compileJava').outputs.files.contains(project.file('build/generated-resources/main')))
        assertTrue(project.tasks.getByName('processResources').outputs.files.contains(project.file('build/generated-resources/resources')))
        assertTrue(project.tasks.getByName('mergeProperties').outputs.files.contains(project.file('build/generated-resources/output')))
    }

    // default module name is the project name.
    @Test
    public void checkModuleNameDefaults() {
        Project project = ProjectBuilder.builder().withName('my_test_project').build()
        project.project.plugins.apply(NbmPlugin)

        assertEquals('my_test_project', project.nbm.moduleName.get())
    }

    // default module name is the project name with dots instead of dashes.
    @Test
    public void checkModuleNameFormat() {
        Project project = ProjectBuilder.builder().withName('my-test-project').build()
        project.project.plugins.apply(NbmPlugin)

        assertEquals('my.test.project', project.nbm.moduleName.get())
    }

    // no implementation version by default
    @Test
    public void checkImplementationVersion() {
        Project project = ProjectBuilder.builder().withName('my-test-project').build()
        project.project.plugins.apply(NbmPlugin)

        assertNull(project.nbm.implementationVersion.getOrNull())
    }
}
