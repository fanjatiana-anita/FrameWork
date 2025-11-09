package utiles;

import java.io.*;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import utiles.RouteHandler;
import class_annotations.Controller;
import method_annotations.Route;

public class ClasspathScanner {

    private ClasspathScanner() {}
    /**
     * Scanne TOUT le classpath et retourne toutes les classes chargées.
     * @return Set de Class<?>
     */
    public static    Map<String, RouteHandler> scanRoutes() {
        Map<String, RouteHandler> routes = new HashMap<>();
        Set<Class<?>> classes = scanAllClasses();

        System.out.println("=== SCAN DES CONTRÔLEURS ===");
        int count = 0;

        for (Class<?> clazz : classes) {
            if (clazz.isAnnotationPresent(Controller.class)) {
                System.out.println("Contrôleur : " + clazz.getName());
                for (Method m : clazz.getDeclaredMethods()) {
                    Route r = m.getAnnotation(Route.class);
                    if (r != null) {
                        routes.put(r.value(), new RouteHandler(clazz, m));
                        System.out.println(" " + r.value() + " → " + m.getName() + "()");
                        count++;
                    }
                }
            }
        }
        System.out.println("=== " + count + " route(s) enregistrée(s) ===");
        return routes;
    }

    public static Set<Class<?>> scanAllClasses() {
        Set<Class<?>> classes = new HashSet<>();
        ClassLoader cl = Thread.currentThread().getContextClassLoader();

        try {
            Enumeration<URL> roots = cl.getResources("");
            while (roots.hasMoreElements()) {
                URL root = roots.nextElement();
                System.out.println("Scan racine classpath : " + root);

                if ("file".equals(root.getProtocol())) {
                    File dir = new File(URLDecoder.decode(root.getPath(), StandardCharsets.UTF_8));
                    if (dir.isDirectory()) {
                        scanDirectory(dir, "", classes);
                    }
                } else if ("jar".equals(root.getProtocol())) {
                    String jarPath = root.getPath();
                    if (jarPath.contains("!")) {
                        String jarFilePath = jarPath.substring(5, jarPath.indexOf('!'));
                        File jarFile = new File(URLDecoder.decode(jarFilePath, StandardCharsets.UTF_8));
                        if (jarFile.exists()) {
                            scanJar(jarFile, classes);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Erreur scan classpath : " + e.getMessage());
        }

        return classes;
    }

    private static void scanDirectory(File dir, String packageName, Set<Class<?>> classes) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                String subPkg = packageName.isEmpty() ? file.getName() : packageName + "." + file.getName();
                scanDirectory(file, subPkg, classes);
            } else if (file.getName().endsWith(".class") && !file.getName().contains("$")) {
                String className = packageName.isEmpty()
                        ? file.getName().substring(0, file.getName().length() - 6)
                        : packageName + "." + file.getName().substring(0, file.getName().length() - 6);
                loadClass(className, classes);
            }
        }
    }

    private static void scanJar(File jarFile, Set<Class<?>> classes) {
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.endsWith(".class") && !name.contains("$")) {
                    String className = name.replace('/', '.').substring(0, name.length() - 6);
                    loadClass(className, classes);
                }
            }
        } catch (IOException ignored) {}
    }

    private static void loadClass(String className, Set<Class<?>> classes) {
        try {
            Class<?> clazz = Class.forName(className, false, Thread.currentThread().getContextClassLoader());
            classes.add(clazz);
        } catch (Throwable ignored) {
            // Ignore les classes non chargables
        }
    }
}