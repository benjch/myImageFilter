package com.benjch.www.utils;

import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class UtilsIntrospection {

    public static List<Class<?>> scanPackage(String packageName) {
        List<Class<?>> classes = new ArrayList<>();

        try {
            String packagePath = packageName.replace('.', '/');
            Enumeration<URL> resources = Thread.currentThread().getContextClassLoader().getResources(packagePath);

            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                String protocol = resource.getProtocol();

                if (protocol.equals("file")) {
                    File packageDirectory = new File(resource.toURI());
                    classes.addAll(findClassesInDirectory(packageName, packageDirectory));
                } else if (protocol.equals("jar")) {
                    JarURLConnection connection = (JarURLConnection) resource.openConnection();
                    JarFile jarFile = connection.getJarFile();
                    classes.addAll(findClassesInJar(packageName, jarFile));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return classes;
    }

    private static List<Class<?>> findClassesInDirectory(String packageName, File packageDirectory) {
        List<Class<?>> classes = new ArrayList<>();

        if (packageDirectory.exists()) {
            File[] files = packageDirectory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        String subPackageName = packageName + "." + file.getName();
                        classes.addAll(findClassesInDirectory(subPackageName, file));
                    } else if (file.getName().endsWith(".class")) {
                        String className = packageName + "." + file.getName().substring(0, file.getName().length() - 6);
                        try {
                            Class<?> clazz = Class.forName(className);
                            classes.add(clazz);
                        } catch (ClassNotFoundException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }

        return classes;
    }

    private static List<Class<?>> findClassesInJar(String packageName, JarFile jarFile) throws IOException {
        List<Class<?>> classes = new ArrayList<>();

        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String entryName = entry.getName().replace('/', '.');
            entryName = entryName.replace('\\', '.');
            if (entryName.startsWith(packageName) && entryName.endsWith(".class")) {
                String className = entryName.replace('/', '.').substring(0, entryName.length() - 6);
                try {
                    Class<?> clazz = Class.forName(className);
                    classes.add(clazz);
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }

        return classes;
    }
}
