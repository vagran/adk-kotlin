module io.github.vagran.adk.log.slf4j {
    requires kotlin.stdlib;
    requires io.github.vagran.adk.log;
    requires slf4j.api;

    exports org.slf4j.impl;
    exports io.github.vagran.adk.log.slf4j.api;
}
