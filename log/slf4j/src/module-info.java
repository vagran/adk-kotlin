module com.ast.adk.log.slf4j {
    requires kotlin.stdlib;
    requires com.ast.adk.log;
    requires slf4j.api;

    exports org.slf4j.impl;
    exports com.ast.adk.log.slf4j.api;
}
