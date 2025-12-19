package edu.noah.monframework.util;

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
 * Best-effort classpath scanner for classes under a base package.
 */
public final class ClassScanner {
    private ClassScanner() {}

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
                        addClassesFromDirectory(packageName, dir, out, cl);
                    } else if ("jar".equals(protocol)) {
                        JarURLConnection conn = (JarURLConnection) resource.openConnection();
                        try (JarFile jar = conn.getJarFile()) {
                            addClassesFromJar(path, jar, out, cl);
                        }
                    } else {
                        String file = URLDecoder.decode(resource.getPath(), "UTF-8");
                        Path dir = Paths.get(file);
                        if (Files.exists(dir)) {
                            addClassesFromDirectory(packageName, dir, out, cl);
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

    private static void addClassesFromDirectory(String packageName, Path dir, List<Class<?>> out, ClassLoader cl)
            throws IOException {
        Files.walk(dir)
                .filter(p -> p.toString().endsWith(".class"))
                .forEach(p -> {
                    String rel = dir.relativize(p).toString();
                    String className = rel.replace(File.separatorChar, '.').replaceAll("\\.class$", "");
                    String fqcn = packageName + "." + className;
                    tryLoadAndAdd(fqcn, out, cl);
                });
    }

    private static void addClassesFromJar(String path, JarFile jar, List<Class<?>> out, ClassLoader cl) {
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

    private static void tryLoadAndAdd(String fqcn, List<Class<?>> out, ClassLoader cl) {
        try {
            Class<?> c = Class.forName(fqcn, false, cl);
            out.add(c);
        } catch (Throwable t) {
            // skip classes that cannot be loaded
        }
    }
}
