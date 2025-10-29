package edu.noah.monframework.tools;

import java.util.ArrayList;
import java.util.List;

public class RouteInfo {
    public enum Source { ANNOTATION, SERVLET_ANNOTATION, FILTER_ANNOTATION, WEB_XML, STATIC_FILE, JSP_FILE }

    private final String url;
    private final Source source;
    private final List<String> handlers = new ArrayList<>();

    public RouteInfo(String url, Source source) {
        this.url = url;
        this.source = source;
    }

    public String getUrl() { return url; }
    public Source getSource() { return source; }
    public List<String> getHandlers() { return handlers; }

    public void addHandler(String h) { handlers.add(h); }

    @Override
    public String toString() {
        return "RouteInfo{" +
                "url='" + url + '\'' +
                ", source=" + source +
                ", handlers=" + handlers +
                '}';
    }
}
