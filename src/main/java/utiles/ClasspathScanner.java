package utiles;

import class_annotations.Controller;
import method_annotations.Route;
import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class ClasspathScanner {
    private ClasspathScanner() {}

    public static Map<String, RouteHandler> scanRoutes() {
        Map<String, RouteHandler> routes = new HashMap<>();
        Set<String> classNames = new HashSet<>();

        System.out.println("=== SCAN DYNAMIQUE DU CLASSPATH ===");
        findAllClassesInClasspath(classNames);

        int count = 0;
        for (String className : classNames) {
            try {
                Class<?> clazz = Class.forName(className, false, Thread.currentThread().getContextClassLoader());
                if (clazz.isAnnotationPresent(Controller.class)) {
                    System.out.println("Contrôleur : " + className);
                    for (Method m : clazz.getDeclaredMethods()) {
                        Route r = m.getAnnotation(Route.class);
                        if (r != null) {
                            routes.put(r.value(), new RouteHandler(clazz, m));
                            System.out.println(" └─ " + r.value() + " → " + m.getName() + "()");
                            count++;
                        }
                    }
                }
            } catch (Throwable ignored) {
                // Ignore les classes non chargables
            }
        }

        System.out.println("=== " + count + " route(s) trouvée(s) ===");
        return routes;
    }

    /**
     * Trouve TOUTES les classes dans le classpath (dossiers + JARs)
     */
    private static void findAllClassesInClasspath(Set<String> classNames) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try {
            Enumeration<URL> roots = cl.getResources("");
            while (roots.hasMoreElements()) {
                URL root = roots.nextElement();
                System.out.println("Scan racine : " + root);

                if ("file".equals(root.getProtocol())) {
                    File dir = new File(root.toURI());
                    scanDirectory(dir, "", classNames);
                } else if ("jar".equals(root.getProtocol())) {
                    String jarPath = root.getPath();
                    if (jarPath.contains("!")) {
                        String filePath = jarPath.substring(5, jarPath.indexOf('!'));
                        File jarFile = new File(java.net.URLDecoder.decode(filePath, "UTF-8"));
                        if (jarFile.exists()) {
                            scanJar(jarFile, classNames);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Erreur scan classpath : " + e.getMessage());
        }
    }

    private static void scanDirectory(File dir, String packageName, Set<String> classNames) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                String subPkg = packageName.isEmpty() ? file.getName() : packageName + "." + file.getName();
                scanDirectory(file, subPkg, classNames);
            } else if (file.getName().endsWith(".class") && !file.getName().contains("$")) {
                String className = packageName.isEmpty()
                    ? file.getName().substring(0, file.getName().length() - 6)
                    : packageName + "." + file.getName().substring(0, file.getName().length() - 6);
                classNames.add(className);
            }
        }
    }

    private static void scanJar(File jarFile, Set<String> classNames) {
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.endsWith(".class") && !name.contains("$")) {
                    String className = name.replace('/', '.').substring(0, name.length() - 6);
                    classNames.add(className);
                }
            }
        } catch (Exception ignored) {}
    }
}