module com.ast.adk.domain {
    requires kotlin.stdlib;
    requires kotlin.reflect;
    requires jdk.httpserver;
    requires com.ast.adk.async;
    requires com.ast.adk.log;
    requires com.ast.adk.json;
    requires com.ast.adk;

    exports com.ast.adk.domain;
    exports com.ast.adk.domain.httpserver;
}
