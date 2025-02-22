package org.gradle.plugins.nbm.integtest

import org.gradle.testkit.runner.TaskOutcome

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime

@SuppressWarnings('BlockStartsWithBlankLine')
class ManifestGenerationTest extends AbstractIntegrationTest {

    def "check default generated manifest file"() {

        given: "Build file with configured nbm plugin (minimum configuration)"
        // Set the moduleName because I have no idea what the project's name is,
        // so can't rely on the default value for that
        buildFile << \
"""
apply plugin: org.gradle.plugins.nbm.NbmPlugin
version = '3.5.6'
nbm {
  moduleName = 'my.test.project'
}
"""
        when: "Generate netbeans module manifest"
        runTasks "generateModuleManifest"

        then: "Default manifest entries exist with correct values."
        def manifest = checkDefaultModuleManifest()

        then: "Entry 'OpenIDE-Module-Public-Packages' exist in manifest with correct value."
        assert '-' == manifest.get('OpenIDE-Module-Public-Packages')

        then: "Entry 'OpenIDE-Module-Implementation-Version' exist in manifest with correct value."
        assert manifest.get('OpenIDE-Module-Implementation-Version') =~ /\d{12}/

        then: "Entry 'OpenIDE-Module-Build-Version' exist not in manifest"
        assert !manifest.containsKey('OpenIDE-Module-Build-Version')
    }

    def "manifest file with layer file"() {

        given: "Build file with configured nbm plugin"
        // Set the moduleName because I have no idea what the project's name is,
        // so can't rely on the default value for that
        buildFile << \
"""
apply plugin: org.gradle.plugins.nbm.NbmPlugin
version = '3.5.6'
nbm {
  moduleName = 'my.test.project'
  layer = 'rootpckg/mypckg/subpckg/layer.xml'
}
"""
        when: "Generate netbeans module manifest"
        runTasks'generateModuleManifest'

        then: "Default manifest entries exist with correct values."
        def manifest = checkDefaultModuleManifest()

        then: "Entry 'OpenIDE-Module-Layer' exists in manifest with correct value."
        assert 'rootpckg/mypckg/subpckg/layer.xml' == manifest.get('OpenIDE-Module-Layer')
    }

    def "manifest file with java dependency"() {

        given: "Build file with configured nbm plugin"
        // Set the moduleName because I have no idea what the project's name is,
        // so can't rely on the default value for that
        buildFile << \
"""
apply plugin: org.gradle.plugins.nbm.NbmPlugin
version = '3.5.6'
nbm {
  moduleName = 'my.test.project'
  javaDependency = 'Java > 1.8'
}
"""
        when: "Generate netbeans module manifest"
        runTasks 'generateModuleManifest'

        then: "Default manifest entries exist with correct values."
        def manifest = checkDefaultModuleManifest()

        then: "Entry 'OpenIDE-Module-Java-Dependencies' exists in manifest with correct value."
        assert manifest.get('OpenIDE-Module-Java-Dependencies') == 'Java > 1.8'
    }

    def "manifest file with autoupdateShowInClient"() {

        given: "Build file with configured nbm plugin"

        def showInClientParam = autoupdateShowInClient != null ? "autoupdateShowInClient = ${autoupdateShowInClient}" : ""
        buildFile << \
"""
apply plugin: org.gradle.plugins.nbm.NbmPlugin
version = '3.5.6'
nbm {
  moduleName = 'my.test.project'
  $showInClientParam
}
"""
        when: "Generate netbeans module manifest"
        runTasks "generateModuleManifest"

        then: "Default manifest entries exist with correct values."
        def manifest = checkDefaultModuleManifest()

        then: "Entry 'AutoUpdate-Show-In-Client' exist in manifest with correct value."
        assert expectedManifestValue == manifest.get('AutoUpdate-Show-In-Client')

        where:
        autoupdateShowInClient | expectedManifestValue
        true                   | 'true'
        false                  | 'false'
        null                   | null
    }

    @SuppressWarnings('Indentation')
    def "manifest file with configured implementation version and build version"() {
        given: "Build file with configured nbm plugin"
        def implementationParam = implementationVersion != null ? "implementationVersion = '$implementationVersion'" : ''
        def buildParam = buildVersion != null ? "buildVersion = '$buildVersion'" : ''
        buildFile << \
"""
apply plugin: org.gradle.plugins.nbm.NbmPlugin
version = '3.5.6'
nbm {
  moduleName = 'my.test.project'
  $implementationParam
  $buildParam
}
"""
        when: "Generate netbeans module manifest"
        runTasks"generateModuleManifest"

        then: "Default manifest entries exist with correct values."
        def manifest = checkDefaultModuleManifest()

        then: "Entry 'OpenIDE-Module-Implementation-Version' is $expectedImplementationVersion."
        if (expectedImplementationVersion != null) {
            assert manifest.get('OpenIDE-Module-Implementation-Version') =~ expectedImplementationVersion
        } else {
            assert !manifest.containsKey('OpenIDE-Module-Implementation-Version')
        }

        then: "Entry 'OpenIDE-Module-Build-Versionn' is $expectedBuildVersion."
        if (expectedBuildVersion != null) {
            assert manifest.get('OpenIDE-Module-Build-Version') =~ expectedBuildVersion
        } else {
            assert !manifest.containsKey('OpenIDE-Module-Build-Version')
        }

        where:
        implementationVersion | buildVersion || expectedImplementationVersion | expectedBuildVersion
        null                  | null         || /\d{12}/                      | null
        null                  | ''           || null                          | null
        null                  | 'myBuild'    || /myBuild/                     | null            // XXX
        'myImplVersion'       | null         || /myImplVersion/               | /\d{12}/
        'myImplVersion'       | ''           || /myImplVersion/               | null
        'myImplVersion'       | 'myBuild'    || /myImplVersion/               | /myBuild/
        ''                    | null         || null                          | /\d{12}/
        ''                    | ''           || null                          | null
        ''                    | 'myBuild'    || null                          | /myBuild/
    }

    def "public packages are added to manifest for sub packages"() {
        // Set the moduleName because I have no idea what the project's name is,
        // so can't rely on the default value for that
        buildFile << \
"""
apply plugin: org.gradle.plugins.nbm.NbmPlugin
version = '3.5.6'
nbm {
  moduleName = 'my.test.project'
  implementationVersion = version

  publicPackages {
    addWithSubPackages(sourceSets.main, 'rootpckg.mypckg')
  }
}
"""

        setupDefaultSources()

        when:
        runTasks'generateModuleManifest'

        then:
        def manifest = checkDefaultModuleManifest()
        assert manifest.get('OpenIDE-Module-Public-Packages') == 'rootpckg.mypckg.subpckg.*, rootpckg.mypckg.subpckg3.*'
    }

    def "public packages are added to manifest for sub packages of root"() {
        // Set the moduleName because I have no idea what the project's name is,
        // so can't rely on the default value for that
        buildFile << \
"""
apply plugin: org.gradle.plugins.nbm.NbmPlugin
version = '3.5.6'
nbm {
  moduleName = 'my.test.project'
  implementationVersion = version

  publicPackages {
    addWithSubPackages(sourceSets.main, 'rootpckg')
  }
}
"""

        setupDefaultSources()

        when:
        runTasks'generateModuleManifest'

        then:
        def manifest = checkDefaultModuleManifest()
        assert manifest.get('OpenIDE-Module-Public-Packages') == 'rootpckg.mypckg.subpckg.*, rootpckg.mypckg.subpckg3.*'
    }

    def "public packages are added explicitly"() {
        // Set the moduleName because I have no idea what the project's name is,
        // so can't rely on the default value for that
        buildFile << \
"""
apply plugin: org.gradle.plugins.nbm.NbmPlugin
version = '3.5.6'
nbm {
  moduleName = 'my.test.project'
  implementationVersion = version

  publicPackages {
    add 'rootpckg.mypckg'
    add 'rootpckg.mypckg.subpckg'
  }
}
"""

        setupDefaultSources()

        when:
        runTasks 'generateModuleManifest'

        then:
        def manifest = checkDefaultModuleManifest()
        assert manifest.get('OpenIDE-Module-Public-Packages') == 'rootpckg.mypckg.*, rootpckg.mypckg.subpckg.*'
    }

    def "public packages are added explicitly with stars"() {
        // Set the moduleName because I have no idea what the project's name is,
        // so can't rely on the default value for that
        buildFile << \
"""
apply plugin: org.gradle.plugins.nbm.NbmPlugin
version = '3.5.6'
nbm {
  moduleName = 'my.test.project'
  implementationVersion = version

  publicPackages {
    add 'rootpckg.mypckg.*'
    add 'rootpckg.mypckg.subpckg.*'
  }
}
"""

        setupDefaultSources()

        when:
        runTasks 'generateModuleManifest'

        then:
        def manifest = checkDefaultModuleManifest()
        assert manifest.get('OpenIDE-Module-Public-Packages') == 'rootpckg.mypckg.*, rootpckg.mypckg.subpckg.*'
    }

    def "public packages are added explicitly with double stars"() {
        // Set the moduleName because I have no idea what the project's name is,
        // so can't rely on the default value for that
        buildFile << \
"""
apply plugin: org.gradle.plugins.nbm.NbmPlugin
version = '3.5.6'
nbm {
  moduleName = 'my.test.project'
  implementationVersion = version

  publicPackages {
    add 'rootpckg.mypckg.**'
    add 'rootpckg.mypckg.subpckg.**'
  }
}
"""

        setupDefaultSources()

        when:
        runTasks 'generateModuleManifest'

        then:
        def manifest = checkDefaultModuleManifest()
        assert manifest.get('OpenIDE-Module-Public-Packages') == 'rootpckg.mypckg.**, rootpckg.mypckg.subpckg.**'
    }

    def "module friends are added to manifest"() {
        // Set the moduleName because I have no idea what the project's name is,
        // so can't rely on the default value for that
        buildFile << \
"""
apply plugin: org.gradle.plugins.nbm.NbmPlugin
version = '3.5.6'
nbm {
  moduleName = 'my.test.project'
  implementationVersion = version

  publicPackages {
    addWithSubPackages(sourceSets.main, 'rootpckg.mypckg')
  }

  moduleFriends {
    add 'com.foo.acme.friend'
  }
}
"""

        setupDefaultSources()

        when:
        runTasks 'generateModuleManifest'

        then:
        def manifest = checkDefaultModuleManifest()
        assert manifest.get('OpenIDE-Module-Friends') == 'com.foo.acme.friend'
    }

    def "manifest task is UP-TO-DATE on second build without any changes (without custom implementation version)"() {

        given: "Build file with configured nbm plugin"
        // Set the moduleName because I have no idea what the project's name is,
        // so can't rely on the default value for that

        buildFile << \
"""
apply plugin: org.gradle.plugins.nbm.NbmPlugin
version = '3.5.6'
nbm {
  moduleName = 'com.foo.acme'
}
"""
        when: "Generate netbeans module manifest"
        println "Run #1"
        runTasks 'generateModuleManifest'

        Path manifestPath1 = getGeneratedManifestPath()
        FileTime lastModifiedTime1 = Files.getLastModifiedTime(manifestPath1)
        def map1 = getGeneratedModuleManifest()

        Thread.sleep(1000) // Ensure build date change (timestamp resolution)

        println "Run #2"
        def result2 = runTasks 'generateModuleManifest'

        def map2 = getGeneratedModuleManifest()
        Path manifestPath2 = getGeneratedManifestPath()
        FileTime lastModifiedTime2 = Files.getLastModifiedTime(manifestPath2)

        then: "Task is UP-TO-DATE on second build"
        assert result2.task(':generateModuleManifest').outcome == TaskOutcome.UP_TO_DATE

        then: "Generated manifest was not modified"
        assert lastModifiedTime1 == lastModifiedTime2

        then: "Content of generated manifest has not changed"
        assert map1 == map2
    }

    def "manifest task is UP-TO-DATE on second build without any changes (with custom implementation version)"() {

        given: "Build file with configured nbm plugin"
        buildFile << \
"""
apply plugin: org.gradle.plugins.nbm.NbmPlugin
version = '3.5.6'
nbm {
  moduleName = 'com.foo.acme'
  implementationVersion = version
}
"""
        when: "Generate netbeans module manifest"
        println "Run #1"
        runTasks 'generateModuleManifest'

        Path manifestPath1 = getGeneratedManifestPath()
        FileTime lastModifiedTime1 = Files.getLastModifiedTime(manifestPath1)
        def map1 = getGeneratedModuleManifest()

        Thread.sleep(1000) // Ensure build date change (timestamp resolution)

        println "Run #2"
        def result2 = runTasks 'generateModuleManifest'

        def map2 = getGeneratedModuleManifest()
        Path manifestPath2 = getGeneratedManifestPath()
        FileTime lastModifiedTime2 = Files.getLastModifiedTime(manifestPath2)

        then: "Task is UP-TO-DATE on second build"
        assert result2.task(':generateModuleManifest').outcome == TaskOutcome.UP_TO_DATE

        then: "Generated manifest was not modified"
        assert lastModifiedTime1 == lastModifiedTime2

        then: "Content of generated manifest has not changed"
        assert map1 == map2
    }

    def "manifest task is not UP-TO-DATE on second build due to changed specification version (without custom implementation version)"() {

        given: "Build file with configured nbm plugin"
        buildFile << \
"""
apply plugin: org.gradle.plugins.nbm.NbmPlugin
version = '3.5.6'
nbm {
  moduleName = 'com.foo.acme'
}
"""
        when: "Generate netbeans module manifest"
        println "Run #1"
        runTasks 'generateModuleManifest'

        Path manifestPath1 = getGeneratedManifestPath()
        FileTime lastModifiedTime1 = Files.getLastModifiedTime(manifestPath1)
        def map1 = getGeneratedModuleManifest()

        Thread.sleep(1000) // Ensure build date change (timestamp resolution)
        buildFile << \
"""
apply plugin: org.gradle.plugins.nbm.NbmPlugin
version = '3.5.7'
nbm {
  moduleName = 'com.foo.acme/1'
}
"""
        println "Run #2"
        def result2 = runTasks 'generateModuleManifest'

        def map2 = getGeneratedModuleManifest()
        Path manifestPath2 = getGeneratedManifestPath()
        FileTime lastModifiedTime2 = Files.getLastModifiedTime(manifestPath2)

        then: "Task is not UP-TO-DATE on second build"
        assert result2.task(':generateModuleManifest').outcome == TaskOutcome.SUCCESS

        then: "Generated manifest was not modified"
        assert lastModifiedTime1 != lastModifiedTime2

        then: "Content of generated manifest has not changed"
        assert map1 != map2
    }

    def "manifest task is not UP-TO-DATE on second build due to changed implementation version (with custom implementation version)"() {

        given: "Build file with configured nbm plugin"
        buildFile << \
"""
apply plugin: org.gradle.plugins.nbm.NbmPlugin
version = '3.5.6'
nbm {
  moduleName = 'com.foo.acme'
  implementationVersion = version
}
"""
        when: "Generate netbeans module manifest"
        println "Run #1"
        runTasks 'generateModuleManifest'

        Path manifestPath1 = getGeneratedManifestPath()
        FileTime lastModifiedTime1 = Files.getLastModifiedTime(manifestPath1)
        def map1 = getGeneratedModuleManifest()

        Thread.sleep(1000) // Ensure build date change (timestamp resolution)
        buildFile << \
"""
apply plugin: org.gradle.plugins.nbm.NbmPlugin
version = '3.5.6'
nbm {
  moduleName = 'com.foo.acme'
  implementationVersion = '3.5.6.1'
}
"""
        println "Run #2"
        def result2 = runTasks 'generateModuleManifest'

        def map2 = getGeneratedModuleManifest()
        Path manifestPath2 = getGeneratedManifestPath()
        FileTime lastModifiedTime2 = Files.getLastModifiedTime(manifestPath2)

        then: "Task is not UP-TO-DATE on second build"
        assert result2.task(':generateModuleManifest').outcome == TaskOutcome.SUCCESS

        then: "Generated manifest was not modified"
        assert lastModifiedTime1 != lastModifiedTime2

        then: "Content of generated manifest has not changed"
        assert map1 != map2
    }

    def setupDefaultSources() {
        createProjectFile('src', 'main', 'java', 'rootpckg', 'mypckg', 'subpckg', 'A.java') << \
"""
package rootpckg.mypckg.subpckg;
public class A { }
"""
        createProjectDir('src', 'main', 'java', 'rootpckg', 'mypckg', 'subpckg2')
        createProjectFile('src', 'main', 'java', 'rootpckg', 'mypckg', 'subpckg3', 'B.java') << \
"""
package rootpckg.mypckg.subpckg3;
public class B { }
"""
    }

    private Map<String, String> checkDefaultModuleManifest() {
        Map<String, String> manifest = getGeneratedModuleManifest()

        assert manifest.get('Manifest-Version') == '1.0'
        assert manifest.get('OpenIDE-Module-Specification-Version') == '3.5.6'
        assert manifest.get('OpenIDE-Module') == 'my.test.project'
        assert manifest.get('OpenIDE-Module-Requires')?.split(',')*.trim().contains('org.openide.modules.ModuleFormat1')
        assert manifest.get('Created-By') == 'Gradle NBM plugin'

        manifest
    }

    private Map<String, String> getGeneratedModuleManifest() {
        ManifestUtils.readManifest(generatedManifestPath)
    }

    private Path getGeneratedManifestPath() {
        new File(buildDir, "generated-manifest.mf").toPath()
    }
}
