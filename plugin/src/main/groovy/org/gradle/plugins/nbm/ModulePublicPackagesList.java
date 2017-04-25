package org.gradle.plugins.nbm;

import org.gradle.api.Project;
import org.gradle.api.tasks.SourceSet;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;

public final class ModulePublicPackagesList {

    private final List<PackageNameGenerator> packageNameGenerators;
    private final Project project;

    public ModulePublicPackagesList(Project project) {
        this.project = project;
        this.packageNameGenerators = new LinkedList<>();
    }

    private static void getPackagesInDir(String packageName, File currentDir, List<String> result) {
        boolean hasFile = false;
        for (File file : currentDir.listFiles()) {
            if (file.isDirectory()) {
                String lastPart = file.getName();
                String subPackageName = packageName.isEmpty() ? lastPart : packageName + "." + lastPart;
                getPackagesInDir(subPackageName, file, result);
            } else if (!hasFile && file.isFile()) {
                hasFile = true;
            }
        }

        if (hasFile) {
            result.add(packageName);
        }
    }

    private static void findAllPackages(File sourceRoot, String packageName, List<String> result) {
        String[] pathParts = packageName.split(Pattern.quote("."));
        File startDir = sourceRoot;
        for (String part : pathParts) {
            startDir = new File(startDir, part);
        }

        if (!startDir.isDirectory()) {
            return;
        }

        getPackagesInDir(packageName, startDir, result);
    }

    private static void findAllPackages(SourceSet sourceSet, String packageName, List<String> result) {
        for (File sourceRoot : sourceSet.getAllJava().getSrcDirs()) {
            findAllPackages(sourceRoot, packageName, result);
        }
    }

    public void addWithSubPackages(final SourceSet sourceSet, final String packageName) {
        Objects.requireNonNull(sourceSet, "sourceSet");
        Objects.requireNonNull(packageName, "packageName");

        packageNameGenerators.add(new PackageNameGenerator() {
            @Override
            public void findPackages(List<String> result) {
                findAllPackages(sourceSet, packageName, result);
            }
        });
    }

    public void add(final String packageName) {
        Objects.requireNonNull(packageName, "packageName");

        packageNameGenerators.add(new PackageNameGenerator() {
            @Override
            public void findPackages(List<String> result) {
                result.add(packageName);
            }
        });
    }

    private List<String> resolvePackageNames() {
        List<String> result = new LinkedList<>();
        for (PackageNameGenerator currentNames : packageNameGenerators) {
            currentNames.findPackages(result);
        }
        return result;
    }

    public SortedSet<String> getEntries() {
        List<String> packageNames = resolvePackageNames();
        SortedSet<String> entries = new TreeSet<>();
        for (String packageName : packageNames) {
            entries.add(toStarImport(packageName));
        }
        return entries;
    }

    @Deprecated
    public List<String> getPackageList() {
        project.getLogger().error(
            "'nbm' plugin: Use of 'friendPackages.getPackageListPattern()' is deprecated use 'publicPackages.getEntries()' instead!");
        return resolvePackageNames();
    }

    @Deprecated
    public List<String> getPackageListPattern() {
        project.getLogger().error(
            "'nbm' plugin: Use of 'friendPackages.getPackageListPattern()' is deprecated use 'publicPackages.getEntries()' instead!");
        return new ArrayList<>(getEntries());
    }

    private static String toStarImport(String packageName) {
        return packageName.endsWith("*") ? packageName : packageName + ".*";
    }

    private interface PackageNameGenerator {
        public void findPackages(List<String> result);
    }
}
