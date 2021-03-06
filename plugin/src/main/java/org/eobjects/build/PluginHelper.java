package org.eobjects.build;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.maven.plugin.MojoFailureException;

public final class PluginHelper {

    public static final String PROPERTY_BASEDIR = "${project.basedir}";

    public static final String PROPERTY_BUILD_DIR = "${project.build.directory}";

    public static PluginHelper get(File basedir, Map<String, String> environment, File dotnetPackOutput,
            String buildConfiguration, boolean skip) {
        return new PluginHelper(basedir, environment, dotnetPackOutput, buildConfiguration, skip);
    }

    private final File basedir;
    private final Map<String, String> environment;
    private final boolean skip;
    private final File dotnetPackOutput;
    private final String buildConfiguration;

    private PluginHelper(File basedir, Map<String, String> environment, File dotnetPackOutput,
            String buildConfiguration, boolean skip) {
        this.basedir = basedir;
        this.environment = environment == null ? Collections.<String, String> emptyMap() : environment;
        this.buildConfiguration = buildConfiguration == null ? "Release" : buildConfiguration;
        this.dotnetPackOutput = dotnetPackOutput == null ? new File("bin") : dotnetPackOutput;
        this.skip = skip;
    }

    public boolean isSkip() {
        return skip;
    }

    private final FileFilter projectJsonDirectoryFilter = new FileFilter() {
        public boolean accept(File dir) {
            if (dir.isDirectory()) {
                if (new File(dir, "project.json").exists()) {
                    return true;
                }
            }
            return false;
        }
    };

    public File getNugetPackageDir(File subDirectory) {
        if (dotnetPackOutput.isAbsolute()) {
            return dotnetPackOutput;
        }
        final File directory = new File(subDirectory, dotnetPackOutput.getPath());
        return directory;
    }

    public File getNugetPackage(File subDirectory) {
        final File packageDirectory = getNugetPackageDir(subDirectory);
        final File[] nugetPackages = packageDirectory.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.endsWith(".nupkg");
            }
        });

        if (nugetPackages == null || nugetPackages.length == 0) {
            throw new IllegalStateException("Could not find NuGet package! ModuleDir=" + subDirectory + ", PackageDir="
                    + packageDirectory + ", PackOutput=" + dotnetPackOutput);
        }

        return nugetPackages[0];
    }

    public File[] getProjectDirectories() throws MojoFailureException {
        return getProjectDirectories(true);
    }

    public File[] getProjectDirectories(boolean throwExceptionWhenNotFound) throws MojoFailureException {
        if (skip) {
            return new File[0];
        }

        final File directory = basedir;
        if (projectJsonDirectoryFilter.accept(directory)) {
            return new File[] { directory };
        }

        final File[] directories = directory.listFiles(projectJsonDirectoryFilter);
        if (directories == null || directories.length == 0) {
            if (throwExceptionWhenNotFound) {
                throw new MojoFailureException("Could not find any directories with a 'project.json' file.");
            } else {
                return new File[0];
            }
        }
        return directories;
    }

    public void executeCommand(File subDirectory, String command) throws MojoFailureException {
        executeCommand(subDirectory, command.split(" "));
    }

    public void executeCommand(File subDirectory, String... command) throws MojoFailureException {
        final ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(subDirectory);
        for (Entry<String, String> entry : environment.entrySet()) {
            final String key = entry.getKey();
            if (key != null) {
                String value = entry.getValue();
                if (value == null) {
                    value = "";
                }
                processBuilder.environment().put(key, value);
            }
        }
        processBuilder.inheritIO();

        final int exitCode;
        try {
            final Process process = processBuilder.start();
            exitCode = process.waitFor();
        } catch (Exception e) {
            throw new MojoFailureException("Command (in " + subDirectory + ") " + Arrays.toString(command) + " failed",
                    e);
        }

        if (exitCode == 0) {
            // success
        } else {
            throw new MojoFailureException("Command (in " + subDirectory + ") " + Arrays.toString(command)
                    + " returned non-zero exit code: " + exitCode);
        }
    }

    public String getBuildConfiguration() {
        return buildConfiguration;
    }

    public boolean isNugetAvailable() {
        // This is pretty clunky, but I think the only manageable way to
        // determine it.
        try {
            final int exitCode = new ProcessBuilder("nuget", "help").start().waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
