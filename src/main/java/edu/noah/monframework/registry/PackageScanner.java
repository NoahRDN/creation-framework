package edu.noah.monframework.registry;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class PackageScanner {

    public static List<Class<?>> getClasses(String packageName) throws ClassNotFoundException {
        List<Class<?>> classes = new ArrayList<>();
        String path = packageName.replace('.', '/');
        URL resource = Thread.currentThread().getContextClassLoader().getResource(path);

        if (resource == null) {
            System.out.println("[Scanner] Aucun package trouvé : " + packageName);
            return classes;
        }

        File directory = new File(resource.getFile());
        if (!directory.exists()) return classes;

        for (String file : directory.list()) {
            if (file.endsWith(".class")) {
                String className = packageName + '.' + file.substring(0, file.length() - 6);
                classes.add(Class.forName(className));
            }
        }

        return classes;
    }
}
