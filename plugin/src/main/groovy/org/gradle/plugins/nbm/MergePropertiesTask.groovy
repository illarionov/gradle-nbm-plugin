package org.gradle.plugins.nbm

import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

import javax.inject.Inject

abstract class MergePropertiesTask extends DefaultTask {

    @Inject
    protected abstract FileSystemOperations getFileSystemOperations()

    @InputFiles
    abstract ListProperty<Directory> getInputDirectories()

    @OutputDirectory
    abstract DirectoryProperty getOutputDir()

    @TaskAction
    void generate() {
        def outputDir = getOutputDir().get().asFile
        def inputDirectories = getInputDirectories().get()

        if (!outputDir.mkdirs() && !outputDir.isDirectory()) {
            throw new IOException("Failed to create generated resources output at ${outputDir}")
        }

        Set<String> paths = new LinkedHashSet<>()
        for (Directory directory : inputDirectories) {
            def tree = directory.asFileTree
            tree.visit { if (!it.file.isDirectory()) paths.add(it.relativePath.pathString) }
        }

        paths.each { String path ->
            // if in both merge else copy
            def dest = new File(outputDir, path).parentFile
            dest.mkdirs()

            def inputFiles = []
            for (def input : inputDirectories) {
                def candidate = input.file(path).asFile
                if (candidate.exists())
                    inputFiles.add(candidate)
            }

            if (inputFiles.size() == 1) {
                fileSystemOperations.copy {
                    from inputFiles.first()
                    into dest
                }
            } else {
                def destFile = new File(outputDir, path)
                def text = new StringBuilder()
                for (File file : inputFiles) {
                    if (text.size() > 0)
                        text.append('\n')

                    text.append(file.text)
                }
                destFile << text.toString()
            }
        }
    }
}
