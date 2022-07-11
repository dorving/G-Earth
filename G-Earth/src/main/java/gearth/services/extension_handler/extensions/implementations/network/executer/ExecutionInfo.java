package gearth.services.extension_handler.extensions.implementations.network.executer;

import gearth.GEarth;
import gearth.misc.OSValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by Jonas on 22/09/18.
 */
public final class ExecutionInfo {

    private final static Logger LOGGER = LoggerFactory.getLogger(ExecutionInfo.class);

    private static final Map<String, String[]> EXTENSION_TYPE_TO_EXECUTION_COMMAND;

    public final static List<String> ALLOWED_EXTENSION_TYPES;
    public final static String EXTENSIONS_DIRECTORY = "Extensions";

    static {

        EXTENSION_TYPE_TO_EXECUTION_COMMAND = new HashMap<>();
        EXTENSION_TYPE_TO_EXECUTION_COMMAND.put("*.jar", new String[]{"java", "-jar", "{path}"});
        EXTENSION_TYPE_TO_EXECUTION_COMMAND.put("*.py", new String[]{"python", "{path}"});
        EXTENSION_TYPE_TO_EXECUTION_COMMAND.put("*.py3", new String[]{"python3", "{path}"});
        EXTENSION_TYPE_TO_EXECUTION_COMMAND.put("*.sh", new String[]{"{path}"});
        EXTENSION_TYPE_TO_EXECUTION_COMMAND.put("*.exe", new String[]{"{path}"});
        EXTENSION_TYPE_TO_EXECUTION_COMMAND.put("*.js", new String[]{"node", "{path}"});

        final String[] extraArgs = {"-p", "{port}", "-f", "{filename}", "-c", "{cookie}"};

        for (String type : EXTENSION_TYPE_TO_EXECUTION_COMMAND.keySet()) {

            final String[] commandShort = EXTENSION_TYPE_TO_EXECUTION_COMMAND.get(type);
            final String[] combined = new String[extraArgs.length + commandShort.length];
            System.arraycopy(commandShort, 0, combined, 0, commandShort.length);
            System.arraycopy(extraArgs, 0, combined, commandShort.length, extraArgs.length);

            EXTENSION_TYPE_TO_EXECUTION_COMMAND.put(type, combined);
        }

        if (autoResolveJava8())
            findJava8Install().ifPresent(javaInstall ->
                    EXTENSION_TYPE_TO_EXECUTION_COMMAND.replace("*.jar", new String[]{javaInstall + "/bin/java", "-jar", "{path}"}));

        ALLOWED_EXTENSION_TYPES = new ArrayList<>(EXTENSION_TYPE_TO_EXECUTION_COMMAND.keySet());
    }

    /**
     * Most extensions are compiled using Java 8,
     * in case of multiple Java installations existing on a machine,
     * having the `--auto-resolve-java-8`  flag passed as a program argument,
     * will let G-Earth attempt to use a Java 8 install.
     *
     * TODO: add support for windows
     * TODO: make future-proof, only use the 1.8 version for extensions compiled with 1.8
     */
    public static boolean autoResolveJava8() {
        return (OSValidator.isUnix() || OSValidator.isMac())
                && GEarth.hasFlag("--auto-resolve-java-8");
    }

    private static Optional<String> findJava8Install() {
        final AtomicReference<String> installLocation = new AtomicReference<>();
        final ProcessBuilder processBuilder = new ProcessBuilder("/usr/libexec/java_home", "-v", "1.8.0");
        try {
            final Process process = processBuilder.start();
            final BufferedReader inputReader = process.inputReader();
            try {
                String line = null;
                while (line == null)
                    line = inputReader.readLine();
                installLocation.set(line);
            } catch (Exception e) {
                LOGGER.error("Failed to read input from process for detecting java installs", e);
            }
            process.waitFor(5000, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOGGER.error("Failed to create process for detecting java installs", e);
        } finally {
            if (installLocation.get() == null)
                LOGGER.warn("Did not find Java 8 install to use, most extensions will not work due to missing JavaFX components.");
            else
                LOGGER.debug("Found Java 8 install at {}", installLocation.get());
        }
        return Optional.ofNullable(installLocation.get());
    }

    public static String[] getExecutionCommand(String type) {
        return EXTENSION_TYPE_TO_EXECUTION_COMMAND.get(type);
    }
}
