package org.gradle.plugins.nbm;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import static org.gradle.plugins.nbm.NbmPluginExtension.MODULE_NAME_PATTERN;

public final class ModuleFriendsList {

    private final Set<String> moduleFriends;
    private final Project project;

    public ModuleFriendsList(Project project) {
        this.project = project;
        this.moduleFriends = new HashSet<>();
    }

    public void add(final String moduleName) {
        Objects.requireNonNull(moduleName, "moduleName");

        if (!MODULE_NAME_PATTERN.matcher(moduleName).matches()) {
            throw new InvalidUserDataException(
                "Illegal module friend name - '" + moduleName + "' (must match '" + MODULE_NAME_PATTERN + "'");
        }
        moduleFriends.add(moduleName);
    }

    public SortedSet<String> getEntries() {
        return new TreeSet<>(moduleFriends);
    }
}
