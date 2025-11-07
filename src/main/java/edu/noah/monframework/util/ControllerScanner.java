package edu.noah.monframework.util;

import edu.noah.monframework.annotation.Controller;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Small utility to scan the classpath for classes annotated with {@link Controller}.
 *
 * Note: this scanner performs a best-effort search using the context classloader. It
 * supports classes located in exploded directories (file:) and in JAR files (jar:).
 */
public final class ControllerScanner {
    private ControllerScanner() {}

    public static List<Class<?>> findControllers(String packageName) {
        return findAllClasses(packageName).stream().filter(c -> c.isAnnotationPresent(Controller.class)).collect(java.util.stream.Collectors.toList());
    }

    /**
     * Find all classes under the given package. This is a best-effort scanner that supports
     * exploded directories and JAR files on the classpath.
     */
    public static List<Class<?>> findAllClasses(String packageName) {
        if (packageName == null) throw new IllegalArgumentException("packageName must not be null");
        List<Class<?>> out = new ArrayList<>();
        String path = packageName.replace('.', '/');
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            Enumeration<URL> resources = cl.getResources(path);
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                String protocol = resource.getProtocol();
                try {
                    if ("file".equals(protocol)) {
                        URI uri = resource.toURI();
                        Path dir = Paths.get(uri);
                        Files.walk(dir)
                                .filter(p -> p.toString().endsWith(".class"))
                                .forEach(p -> {
                                    String rel = dir.relativize(p).toString();
                                    String className = rel.replace(File.separatorChar, '.').replaceAll("\\.class$", "");
                                    String fqcn = packageName + "." + className;
                                    tryLoadAndAdd(fqcn, out, cl);
                                });
                    } else if ("jar".equals(protocol)) {
                        JarURLConnection conn = (JarURLConnection) resource.openConnection();
                        try (JarFile jar = conn.getJarFile()) {
                            Enumeration<JarEntry> entries = jar.entries();
                            while (entries.hasMoreElements()) {
                                JarEntry entry = entries.nextElement();
                                String name = entry.getName();
                                if (name.startsWith(path) && name.endsWith(".class")) {
                                    String fqcn = name.replace('/', '.').replaceAll("\\.class$", "");
                                    tryLoadAndAdd(fqcn, out, cl);
                                }
                            }
                        }
                    } else {
                        // fallback: attempt to handle decoded file path
                        String file = URLDecoder.decode(resource.getPath(), "UTF-8");
                        Path dir = Paths.get(file);
                        if (Files.exists(dir)) {
                            Files.walk(dir)
                                    .filter(p -> p.toString().endsWith(".class"))
                                    .forEach(p -> {
                                        String rel = dir.relativize(p).toString();
                                        String className = rel.replace(File.separatorChar, '.').replaceAll("\\.class$", "");
                                        String fqcn = packageName + "." + className;
                                        tryLoadAndAdd(fqcn, out, cl);
                                    });
                        }
                    }
                } catch (Exception e) {
                    // ignore and continue scanning other resources
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return out;
    }

    private static void tryLoadAndAdd(String fqcn, List<Class<?>> out, ClassLoader cl) {
        try {
            Class<?> c = Class.forName(fqcn, false, cl);
            if (c.isAnnotationPresent(Controller.class)) {
                out.add(c);
            }
        } catch (Throwable t) {
            // skip classes that cannot be loaded
        }
    }
}
