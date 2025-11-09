package edu.noah.monframework.web;

/**
 * Simple holder used by controller methods to signal that the request should be
 * forwarded to a specific view (JSP, HTML, etc.) via a RequestDispatcher.
 */
public class ModelView {
    private String view;

    public ModelView() {
    }

    public ModelView(String view) {
        this.view = view;
    }

    /** Logical path to the resource that should render the response. */
    public String getView() {
        return view;
    }

    public void setView(String view) {
        this.view = view;
    }
}
