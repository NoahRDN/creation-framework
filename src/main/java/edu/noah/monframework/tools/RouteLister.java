package edu.noah.monframework.tools;

import edu.noah.monframework.annotation.InvokeMe;
import edu.noah.monframework.util.ControllerScanner;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

/**
 * Utility that collects routes from different sources: annotated methods, web.xml servlet-mappings,
 * and static files under the webapp folder.
 */
public class RouteLister {

    /** Collect routes from annotations, web.xml and static files.
     * @param basePackage package root to scan for @InvokeMe
     * @param webappDirPath path to webapp folder (e.g. src/main/webapp)
     */
    public static List<RouteInfo> collectAll(String basePackage, String webappDirPath) {
        Map<String, RouteInfo> map = new TreeMap<>();

        // 1) annotated methods - use in-project scanner
        try {
            List<Class<?>> classes = ControllerScanner.findAllClasses(basePackage);
            for (Class<?> ci : classes) {
                for (java.lang.reflect.Method mi : ci.getDeclaredMethods()) {
                    if (mi.isAnnotationPresent(InvokeMe.class)) {
                        InvokeMe ann = mi.getAnnotation(InvokeMe.class);
                        String path = (ann == null) ? "" : ann.path();
                        final String routePath = path;
                        RouteInfo ri = map.computeIfAbsent(routePath, k -> new RouteInfo(k, RouteInfo.Source.ANNOTATION));
                        ri.addHandler(ci.getName() + "#" + mi.getName() + (java.lang.reflect.Modifier.isStatic(mi.getModifiers()) ? "[static]" : "[instance]"));
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to scan annotations: " + e.getMessage());
        }

        // 2) web.xml servlet-mappings
        if (webappDirPath != null) {
            Path webxml = Paths.get(webappDirPath, "WEB-INF", "web.xml");
            if (Files.exists(webxml)) {
                try {
                    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                    DocumentBuilder db = dbf.newDocumentBuilder();
                    Document doc = db.parse(webxml.toFile());

                    // read servlet-name -> servlet-class
                    Map<String, String> servletClassByName = new HashMap<>();
                    NodeList servletNodes = doc.getElementsByTagName("servlet");
                    for (int i = 0; i < servletNodes.getLength(); i++) {
                        org.w3c.dom.Node node = servletNodes.item(i);
                        NodeList kids = node.getChildNodes();
                        String name = null, clazz = null;
                        for (int j = 0; j < kids.getLength(); j++) {
                            org.w3c.dom.Node kid = kids.item(j);
                            if (kid.getNodeName().equals("servlet-name")) name = kid.getTextContent().trim();
                            if (kid.getNodeName().equals("servlet-class")) clazz = kid.getTextContent().trim();
                        }
                        if (name != null && clazz != null) servletClassByName.put(name, clazz);
                    }

                    NodeList mappingNodes = doc.getElementsByTagName("servlet-mapping");
                    for (int i = 0; i < mappingNodes.getLength(); i++) {
                        org.w3c.dom.Node node = mappingNodes.item(i);
                        NodeList kids = node.getChildNodes();
                        String name = null; final String[] patternHolder = new String[1];
                        String pattern = null;
                        for (int j = 0; j < kids.getLength(); j++) {
                            org.w3c.dom.Node kid = kids.item(j);
                                if (kid.getNodeName().equals("servlet-name")) name = kid.getTextContent().trim();
                                if (kid.getNodeName().equals("url-pattern")) patternHolder[0] = kid.getTextContent().trim();
                        }
                            pattern = patternHolder[0];
                            if (pattern != null) {
                                String clazz = servletClassByName.getOrDefault(name, "(unknown)");
                                final String routePattern = pattern;
                                RouteInfo ri = map.computeIfAbsent(routePattern, k -> new RouteInfo(k, RouteInfo.Source.WEB_XML));
                                ri.addHandler(clazz + " (from web.xml servlet-mapping)");
                            }
                    }
                } catch (Exception e) {
                    System.err.println("Failed to parse web.xml: " + e.getMessage());
                }
            }
        }

        // 2b) classes annotated with @WebServlet (jakarta / javax)
        // 2b) classes annotated with @WebServlet (jakarta / javax) - use reflection on scanned classes
        try {
            List<Class<?>> classes = ControllerScanner.findAllClasses(basePackage);
            String[] servletAnnNames = new String[]{"jakarta.servlet.annotation.WebServlet", "javax.servlet.annotation.WebServlet"};
            for (Class<?> ci : classes) {
                for (java.lang.annotation.Annotation ann : ci.getAnnotations()) {
                    String annName = ann.annotationType().getName();
                    for (String candidate : servletAnnNames) {
                        if (candidate.equals(annName)) {
                            List<String> patterns = new ArrayList<>();
                            try {
                                java.lang.reflect.Method mVal = ann.annotationType().getMethod("value");
                                Object v = mVal.invoke(ann);
                                if (v != null) {
                                    if (v.getClass().isArray()) for (Object o : (Object[]) v) patterns.add(o.toString()); else patterns.add(v.toString());
                                }
                            } catch (NoSuchMethodException ignored) {}
                            try {
                                java.lang.reflect.Method mUrl = ann.annotationType().getMethod("urlPatterns");
                                Object v2 = mUrl.invoke(ann);
                                if (v2 != null) {
                                    if (v2.getClass().isArray()) for (Object o : (Object[]) v2) patterns.add(o.toString()); else patterns.add(v2.toString());
                                }
                            } catch (NoSuchMethodException ignored) {}

                            for (String p : patterns) {
                                RouteInfo ri = map.computeIfAbsent(p, k -> new RouteInfo(k, RouteInfo.Source.SERVLET_ANNOTATION));
                                ri.addHandler(ci.getName() + " (@WebServlet)");
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to scan for @WebServlet annotations: " + e.getMessage());
        }

        // 2c) classes annotated with @WebFilter (jakarta / javax)
        // 2c) classes annotated with @WebFilter (jakarta / javax) - use reflection on scanned classes
        try {
            List<Class<?>> classes = ControllerScanner.findAllClasses(basePackage);
            String[] filterAnnNames = new String[]{"jakarta.servlet.annotation.WebFilter", "javax.servlet.annotation.WebFilter"};
            for (Class<?> ci : classes) {
                for (java.lang.annotation.Annotation ann : ci.getAnnotations()) {
                    String annName = ann.annotationType().getName();
                    for (String candidate : filterAnnNames) {
                        if (candidate.equals(annName)) {
                            List<String> patterns = new ArrayList<>();
                            try {
                                java.lang.reflect.Method mVal = ann.annotationType().getMethod("value");
                                Object v = mVal.invoke(ann);
                                if (v != null) {
                                    if (v.getClass().isArray()) for (Object o : (Object[]) v) patterns.add(o.toString()); else patterns.add(v.toString());
                                }
                            } catch (NoSuchMethodException ignored) {}
                            try {
                                java.lang.reflect.Method mUrl = ann.annotationType().getMethod("urlPatterns");
                                Object v2 = mUrl.invoke(ann);
                                if (v2 != null) {
                                    if (v2.getClass().isArray()) for (Object o : (Object[]) v2) patterns.add(o.toString()); else patterns.add(v2.toString());
                                }
                            } catch (NoSuchMethodException ignored) {}

                            for (String p : patterns) {
                                RouteInfo ri = map.computeIfAbsent(p, k -> new RouteInfo(k, RouteInfo.Source.FILTER_ANNOTATION));
                                ri.addHandler(ci.getName() + " (@WebFilter)");
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to scan for @WebFilter annotations: " + e.getMessage());
        }

        // 2d) web.xml filter-mapping
        if (webappDirPath != null) {
            Path webxml = Paths.get(webappDirPath, "WEB-INF", "web.xml");
            if (Files.exists(webxml)) {
                try {
                    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                    DocumentBuilder db = dbf.newDocumentBuilder();
                    Document doc = db.parse(webxml.toFile());

                    // read filter-name -> filter-class
                    Map<String, String> filterClassByName = new HashMap<>();
                    NodeList filterNodes = doc.getElementsByTagName("filter");
                    for (int i = 0; i < filterNodes.getLength(); i++) {
                        org.w3c.dom.Node node = filterNodes.item(i);
                        NodeList kids = node.getChildNodes();
                        String name = null, clazz = null;
                        for (int j = 0; j < kids.getLength(); j++) {
                            org.w3c.dom.Node kid = kids.item(j);
                            if (kid.getNodeName().equals("filter-name")) name = kid.getTextContent().trim();
                            if (kid.getNodeName().equals("filter-class")) clazz = kid.getTextContent().trim();
                        }
                        if (name != null && clazz != null) filterClassByName.put(name, clazz);
                    }

                    NodeList filterMappingNodes = doc.getElementsByTagName("filter-mapping");
                    for (int i = 0; i < filterMappingNodes.getLength(); i++) {
                        org.w3c.dom.Node node = filterMappingNodes.item(i);
                        NodeList kids = node.getChildNodes();
                        String name = null; final String[] patternHolder = new String[1];
                        String pattern = null;
                        for (int j = 0; j < kids.getLength(); j++) {
                            org.w3c.dom.Node kid = kids.item(j);
                            if (kid.getNodeName().equals("filter-name")) name = kid.getTextContent().trim();
                            if (kid.getNodeName().equals("url-pattern")) patternHolder[0] = kid.getTextContent().trim();
                        }
                        pattern = patternHolder[0];
                        if (pattern != null) {
                            String clazz = filterClassByName.getOrDefault(name, "(unknown)");
                            final String routePattern = pattern;
                            RouteInfo ri = map.computeIfAbsent(routePattern, k -> new RouteInfo(k, RouteInfo.Source.WEB_XML));
                            ri.addHandler(clazz + " (from web.xml filter-mapping)");
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Failed to parse web.xml filter-mapping: " + e.getMessage());
                }
            }
        }

        // 3) static files under webapp
        if (webappDirPath != null) {
            try {
                Path root = Paths.get(webappDirPath);
                if (Files.exists(root)) {
                    List<Path> files = Files.walk(root)
                            .filter(Files::isRegularFile)
                            .collect(Collectors.toList());
                    for (Path p : files) {
                        String rel = root.relativize(p).toString().replace(File.separatorChar, '/');
                        String url = "/" + rel;
                        if (url.endsWith(".jsp")) {
                            map.computeIfAbsent(url, k -> new RouteInfo(k, RouteInfo.Source.JSP_FILE));
                        } else {
                            map.computeIfAbsent(url, k -> new RouteInfo(k, RouteInfo.Source.STATIC_FILE));
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Failed to collect static files: " + e.getMessage());
            }
        }

        return new ArrayList<>(map.values());
    }
}
