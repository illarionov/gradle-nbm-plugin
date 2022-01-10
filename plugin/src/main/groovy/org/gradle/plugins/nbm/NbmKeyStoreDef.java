package org.gradle.plugins.nbm;

import org.gradle.api.Project;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;

import javax.inject.Inject;

import java.io.File;

public class NbmKeyStoreDef {

    private final Project project;

    private final RegularFileProperty keyStoreFile;
    private final Property<String> username;
    private final Property<String> password;

    @Inject
    public NbmKeyStoreDef(ObjectFactory objectFactory, Project project) {
        this.project = project;
        this.keyStoreFile = objectFactory.fileProperty();
        this.username = objectFactory.property(String.class);
        this.password = objectFactory.property(String.class);
    }

    @InputFile
    @Optional
    Provider<RegularFile> getKeyStoreFile() {
        return keyStoreFile;
    }

    public void setKeyStoreFile(File file) {
        keyStoreFile.set(file);
    }

    public void setKeyStoreFile(Provider<? extends RegularFile> fileProvider) {
        keyStoreFile.set(fileProvider);
    }

    public void setKeyStoreFile(Object file) {
        keyStoreFile.set(project.file(file));
    }

    @Input
    @Optional
    Provider<String> getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username.set(username);
    }

    public void setUsername(Provider<String> usernameProvider) {
        this.username.set(usernameProvider);
    }

    @Input
    @Optional
    Provider<String> getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password.set(password);
    }

    public void setPassword(Provider<String> passwordProvider) {
        this.password.set(passwordProvider);
    }
}
