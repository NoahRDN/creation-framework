package edu.noah.monframework.web;

/**
 * Simple holder used by controller methods to signal that the request should be
 * forwarded to a specific view (JSP, HTML, etc.) via a RequestDispatcher.
 */
public class ModelView {
    private String view;
    private final java.util.Map<String, Object> model = new java.util.LinkedHashMap<>();

    public ModelView() {
    }

    public ModelView(String view) {
        this.view = view;
    }

    /**
     * Convenience constructor to set both the view and initial model data.
     */
    public ModelView(String view, java.util.Map<String, Object> data) {
        this.view = view;
        setModel(data);
    }

    /** Logical path to the resource that should render the response. */
    public String getView() {
        return view;
    }

    public void setView(String view) {
        this.view = view;
    }

    /** Returns an immutable view of the model attributes. */
    public java.util.Map<String, Object> getModel() {
        return java.util.Collections.unmodifiableMap(model);
    }

    /** Replace the model content with the provided entries (null clears). */
    public void setModel(java.util.Map<String, Object> data) {
        model.clear();
        if (data != null) model.putAll(data);
    }

    /** Add or replace a single attribute in the model. */
    public ModelView addAttribute(String name, Object value) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Attribute name must not be null or blank");
        }
        model.put(name, value);
        return this;
    }
}
