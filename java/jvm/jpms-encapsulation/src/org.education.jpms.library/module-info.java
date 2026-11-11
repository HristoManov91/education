module org.education.jpms.library {
    exports org.education.jpms.library.api;
    // Framework reflection is explicitly allowed; the application is not.
    opens org.education.jpms.library.internal to org.education.jpms.framework;
}
