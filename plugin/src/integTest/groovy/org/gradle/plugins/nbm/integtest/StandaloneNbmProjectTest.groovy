package org.gradle.plugins.nbm.integtest

import groovy.xml.XmlSlurper
import groovy.xml.slurpersupport.GPathResult
import org.xml.sax.EntityResolver
import org.xml.sax.InputSource
import org.xml.sax.SAXException

import javax.xml.parsers.SAXParserFactory

import java.nio.file.Files
import java.util.jar.Attributes
import java.util.jar.JarFile
import java.util.regex.Pattern
import java.util.stream.Collectors

import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.hasItem
import static org.hamcrest.Matchers.not

class StandaloneNbmProjectTest extends AbstractIntegrationTest {
    def "load project"() {
        buildFile << \
"""
apply plugin: 'java'
apply plugin: org.gradle.plugins.nbm.NbmPlugin

"""
        when:
        def result = runTasks 'tasks'

        then:
        result.output =~ /(?m)^nbm\b/
    }

    def "run nbm without module name "() {
        buildFile << \
"""
apply plugin: 'java'
apply plugin: org.gradle.plugins.nbm.NbmPlugin

"""
        when:
        runTasks'nbm'

        then:
        def moduleName = integTestDir.name
        assertThat(getInBuildDir("nbm/${moduleName}.nbm"), FileMatchers.exists())
    }

    def "run nbm"() {
        buildFile << \
"""
apply plugin: 'java'
apply plugin: org.gradle.plugins.nbm.NbmPlugin

nbm {
  moduleName = 'com.foo.acme'
}
"""
        when:
        runTasks 'nbm'

        then:
        // TODO expect output file with all required entries
        [
            'module/config/Modules/com-foo-acme.xml',
            'module/modules/com-foo-acme.jar',
            'module/update_tracking/com-foo-acme.xml',
            'nbm/com-foo-acme.nbm'
        ].each {
            assertThat getInBuildDir(it), FileMatchers.exists()
        }
    }

    def "build signed nbm"() {
        buildFile << \
"""
apply plugin: 'java'
apply plugin: org.gradle.plugins.nbm.NbmPlugin

nbm {
  moduleName = 'com.foo.acme'
  keyStore {
    keyStoreFile = project.file('keystore')
    username = 'myself'
    password = 'specialsauce'
  }
}
"""
        when:
        Files.copy(StandaloneNbmProjectTest.getResourceAsStream('keystore'),
            getIntegTestDir().toPath().resolve('keystore'))

        runTasks 'nbm'

        then:
        assertThat(new File(buildDir, 'nbm/com-foo-acme.nbm'), FileMatchers.exists())
    }

    def "build with module dependency"() {
        buildFile << \
"""
apply plugin: 'java'
apply plugin: org.gradle.plugins.nbm.NbmPlugin

nbm {
  moduleName = 'com.foo.acme'
}
dependencies {
  annotationProcessor 'org.netbeans.api:org-openide-awt:${nbVersion}'
  implementation 'org.netbeans.api:org-openide-awt:${nbVersion}'
  implementation 'org.netbeans.api:org-openide-util:${nbVersion}'
}
"""
        def srcDir = createNewDir(integTestDir, 'src/main/java/com/mycompany/standalone')
        createNewFile(srcDir, 'Service.java') << \
"""
package com.mycompany.standalone;
public interface Service {
    void action();
}
"""
        createNewFile(srcDir, 'ServiceImpl.java') << \
"""
package com.mycompany.standalone;
import org.openide.util.lookup.ServiceProvider;

@ServiceProvider(service = Service.class)
public class ServiceImpl implements Service {

    @Override public void action() {
        org.openide.util.Utilities.isUnix();
    }
}
"""
        createNewFile(srcDir, 'HelloAction.java') << \
"""
package com.mycompany.standalone;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle.Messages;

@ActionID(
        category = "Help",
        id = "com.mycompany.standalone.HelloAction"
)
@ActionRegistration(
        displayName = "#CTL_HelloAction"
)
@ActionReference(path = "Menu/Help", position = 100)
@Messages("CTL_HelloAction=Say hello")
public final class HelloAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        // TODO implement action body
    }
}
"""
        def resDir = createNewDir(integTestDir, 'src/main/resources/com/mycompany/standalone')
        createNewFile(resDir, 'Bundle.properties') << \
"""
MyKey=value
"""

        when:
        runTasks 'netbeans'
        def moduleJar = getInBuildDir('module/modules/com-foo-acme.jar')

        then:
        [
            'classes/java/main/META-INF/services/com.mycompany.standalone.Service',
            'module/config/Modules/com-foo-acme.xml',
            'module/update_tracking/com-foo-acme.xml',
            'module/.lastModified'
        ].each {
            assertThat getInBuildDir(it), FileMatchers.exists()
        }

        assertThat moduleJar, FileMatchers.exists()
        assertThat moduleDependencies(moduleJar), hasItem('org.openide.util > 9.19')
        assertThat moduleDependencies(moduleJar), hasItem('org.openide.awt > 7.80')
        moduleProperties(moduleJar, 'com/mycompany/standalone/Bundle.properties').getProperty('MyKey') == 'value'
        moduleProperties(moduleJar, 'com/mycompany/standalone/Bundle.properties').getProperty('CTL_HelloAction') == 'Say hello'
    }

    def "build with extra JAR"() {
        buildFile << \
"""
apply plugin: 'java'
apply plugin: org.gradle.plugins.nbm.NbmPlugin

nbm {
  moduleName = 'com.foo.acme'
}
dependencies {
  implementation 'org.netbeans.api:org-openide-util:${nbVersion}'
  implementation 'org.slf4j:slf4j-api:1.7.2'
}
"""
        def srcDir = createNewDir(integTestDir, 'src/main/java/com/mycompany/standalone')
        createNewFile(srcDir, 'Service.java') << \
"""
package com.mycompany.standalone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
public class Service {
  public Service () {
    Logger logger = LoggerFactory.getLogger(Service.class);
  }
}
"""

        when:
        runTasks 'netbeans'
        def moduleJar = getInBuildDir'module/modules/com-foo-acme.jar'

        then:
        assertThat(moduleJar, FileMatchers.exists())
        assertThat(getInBuildDir('module/modules/ext/slf4j-api-1.7.2.jar'), FileMatchers.exists())
        assertThat(getInBuildDir("module/modules/ext/org-openide-util-lookup-${nbVersion}.jar"), not(FileMatchers.exists()))

        assertThat(moduleClasspath(moduleJar), hasItem('ext/slf4j-api-1.7.2.jar'))
    }

    def "build with extra JAR in classpathExtFolder"() {
        buildFile << \
"""
apply plugin: 'java'
apply plugin: org.gradle.plugins.nbm.NbmPlugin

nbm {
  moduleName = 'com.foo.acme'
  classpathExtFolder = 'acme'
}
dependencies {
  implementation 'org.netbeans.api:org-openide-util:${nbVersion}'
  implementation 'org.slf4j:slf4j-api:1.7.2'
}
"""
        def srcDir = createNewDir(integTestDir, 'src/main/java/com/mycompany/standalone')
        createNewFile(srcDir, 'Service.java') << \
"""
package com.mycompany.standalone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
public class Service {
  public Service () {
    Logger logger = LoggerFactory.getLogger(Service.class);
  }
}
"""

        when:
        runTasks 'netbeans'
        def moduleJar = getInBuildDir 'module/modules/com-foo-acme.jar'

        then:
        assertThat(moduleJar, FileMatchers.exists())
        assertThat(getInBuildDir('module/modules/ext/acme/slf4j-api-1.7.2.jar'), FileMatchers.exists())
        assertThat(getInBuildDir("module/modules/ext/acme/org-openide-util-lookup-${nbVersion}.jar"), not(FileMatchers.exists()))
        assertThat(getInBuildDir("module/modules/ext/org-openide-util-lookup-${nbVersion}.jar"), not(FileMatchers.exists()))

        assertThat(moduleClasspath(moduleJar), hasItem('ext/acme/slf4j-api-1.7.2.jar'))
    }

    def "build with no cluster defined"() {
        buildFile << \
"""
apply plugin: 'java'
apply plugin: org.gradle.plugins.nbm.NbmPlugin

nbm {
  moduleName = 'com.foo.acme'
}
"""
        when:
        runTasks 'nbm'
        File module = getInBuildDir'nbm/com-foo-acme.nbm'

        then:
        // TODO expect output file with all required entries
        [
            'module/config/Modules/com-foo-acme.xml',
            'module/modules/com-foo-acme.jar',
            'module/update_tracking/com-foo-acme.xml'
        ].each {
            assertThat getInBuildDir(it), FileMatchers.exists()
        }

        assertThat module, FileMatchers.exists()
        moduleXml(module, 'Info/info.xml').getProperty('@targetcluster').text().isEmpty()

        def moduleXml = moduleXml(module, 'netbeans/config/Modules/com-foo-acme.xml')
        assert !moduleXml.param.find { it.@name == 'autoload' }.toBoolean()
        assert !moduleXml.param.find { it.@name == 'eager' }.toBoolean()
        assert moduleXml.param.find { it.@name == 'enabled' }.toBoolean()
    }

    def "build with cluster defined that is not called 'extra'"() {
        buildFile << \
"""
apply plugin: 'java'
apply plugin: org.gradle.plugins.nbm.NbmPlugin

nbm {
  cluster = 'myCluster'
  moduleName = 'com.foo.acme'
}
"""
        when:
        runTasks 'nbm'
        File module = getInBuildDir 'nbm/com-foo-acme.nbm'

        then:
        // TODO expect output file with all required entries
        assertThat(module, FileMatchers.exists())
        [
            'module/config/Modules/com-foo-acme.xml',
            'module/modules/com-foo-acme.jar',
            'module/update_tracking/com-foo-acme.xml'
        ].each {
            assertThat getInBuildDir(it), FileMatchers.exists()
        }

        moduleXml(module, 'Info/info.xml').getProperty('@targetcluster').text() == "myCluster"
    }

    def "build with cluster defined that is called 'extra'"() {
        buildFile << \
"""
apply plugin: 'java'
apply plugin: org.gradle.plugins.nbm.NbmPlugin

nbm {
  cluster = 'extra'
  moduleName = 'com.foo.acme'
}
"""
        when:
        runTasks 'nbm'
        File module = getInBuildDir 'nbm/com-foo-acme.nbm'

        then:
        // TODO expect output file with all required entries
        assertThat(module, FileMatchers.exists())
        [
            'module/config/Modules/com-foo-acme.xml',
            'module/modules/com-foo-acme.jar',
            'module/update_tracking/com-foo-acme.xml'
        ].each {
            assertThat getInBuildDir(it), FileMatchers.exists()
        }

        moduleXml(module, 'Info/info.xml').getProperty('@targetcluster').text().isEmpty()
    }

    def "build autoload module"() {
        buildFile << \
"""
apply plugin: 'java'
apply plugin: org.gradle.plugins.nbm.NbmPlugin

nbm {
  moduleName = 'com.foo.acme'
  autoload = 'true'
}
"""
        when:
        runTasks 'nbm'
        File module = getInBuildDir('nbm/com-foo-acme.nbm')

        then:
        assertThat(getInBuildDir('module/config/Modules/com-foo-acme.xml'), FileMatchers.exists())
        def moduleXml = moduleXml(module, 'netbeans/config/Modules/com-foo-acme.xml')
        assert moduleXml.param.find { it.@name == 'autoload' }.toBoolean()
        assert !moduleXml.param.find { it.@name == 'eager' }.toBoolean()
        assert !moduleXml.param.find { it.@name == 'enabled' }.toBoolean()
    }

    def "build eager module"() {
        buildFile << \
"""
apply plugin: 'java'
apply plugin: org.gradle.plugins.nbm.NbmPlugin

nbm {
  moduleName = 'com.foo.acme'
  eager = 'true'
}
"""
        when:
        runTasks 'nbm'
        File module = getInBuildDir'nbm/com-foo-acme.nbm'

        then:
        assertThat(getInBuildDir('module/config/Modules/com-foo-acme.xml'), FileMatchers.exists())
        def moduleXml = moduleXml(module, 'netbeans/config/Modules/com-foo-acme.xml')

        assert !moduleXml.param.find { it.@name == 'autoload' }.toBoolean()
        assert moduleXml.param.find { it.@name == 'eager' }.toBoolean()
        assert !moduleXml.param.find { it.@name == 'enabled' }.toBoolean()
    }

    def "use generated bundle class"() {
        buildFile << \
 """
apply plugin: 'java'
apply plugin: org.gradle.plugins.nbm.NbmPlugin

dependencies {
  annotationProcessor 'org.netbeans.api:org-openide-util:${nbVersion}'
  implementation 'org.netbeans.api:org-openide-awt:${nbVersion}'
  implementation 'org.netbeans.api:org-openide-util:${nbVersion}'
  testImplementation "junit:junit:4.11"
}

test {
  useJUnit()
  dependsOn 'nbm'
}
"""
        def srcDir = createNewDir(integTestDir, 'src/main/java/com/mycompany/standalone')
        createNewFile(srcDir, 'Main.java') << \
"""
package com.mycompany.standalone;
import org.openide.util.NbBundle;
@NbBundle.Messages("TEST_HelloWorld=Hello, World!")
public class Main {
    public static String hello() {
        return Bundle.TEST_HelloWorld();
    }
}
"""
        def testDir = createNewDir(integTestDir, 'src/test/java/com/mycompany/standalone')
        createNewFile(testDir, 'MainTest.java') << \
"""
package com.mycompany.standalone;
public class MainTest {
    @org.junit.Test
    public void testMessage() {
        org.junit.Assert.assertEquals("Hello, World!", Main.hello());
    }
}
"""
        def resDir = createNewDir(integTestDir, 'src/main/resources/com/mycompany/standalone')
        createNewFile(resDir, 'Bundle.properties') << \
"""
MyKey=value
"""

        when:
        runTasks 'test'

        then:
        assertThat(getInBuildDir('classes/java/main/com/mycompany/standalone/Bundle.class'), FileMatchers.exists())
    }

    private Iterable<String> moduleDependencies(File jarFile) {
        JarFile jar = new JarFile(jarFile)
        def attrs = jar.manifest?.mainAttributes
        def attrValue = attrs?.getValue(new Attributes.Name('OpenIDE-Module-Module-Dependencies'))
        jar.close()
        Pattern.compile(",")
            .splitAsStream(attrValue != null ? attrValue : '')
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList())
    }

    private Iterable<String> moduleClasspath(File jarFile) {
        JarFile jar = new JarFile(jarFile)
        def attrs = jar.manifest?.mainAttributes
        def attrValue = attrs.getValue(new Attributes.Name('Class-Path'))
        jar.close()
        Pattern.compile(",")
            .splitAsStream(attrValue != null ? attrValue : '')
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList())
    }

    private Properties moduleProperties(File jarFile, String resourceName) {
        JarFile jar = new JarFile(jarFile)
        def is = jar.getInputStream(jar.getEntry(resourceName))
        def props = new Properties()
        props.load(is)
        is.close()
        jar.close()
        props
    }

    private GPathResult moduleXml(File jarFile, String resourceName) {
        new JarFile(jarFile).withCloseable { jar ->
            jar.getInputStream(jar.getEntry(resourceName)).withCloseable { is ->
                def factory = SAXParserFactory.newInstance()

                // Don't lookup external resources - relying on some external resource to fetch over and
                // over isn't ideal from a stability standpoint.
                def resolver = new EntityResolver() {
                    @Override
                    InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
                        def localCopy = cm.catalog.resolveEntity(null, publicId, systemId)
                        if (localCopy == null)
                            throw new IllegalStateException("Could not DTD find file in catalogue: pub = ${publicId} sys = ${systemId}")
                        return new InputSource(localCopy)
                    }
                }

                def reader = factory.newSAXParser().getXMLReader()
                reader.entityResolver = resolver

                return new XmlSlurper(reader).parse(is)
            }
        }
    }
}
