module io.github.vagran.adk.domain {
    requires kotlin.stdlib;
    requires kotlin.reflect;
    requires jdk.httpserver;
    requires io.github.vagran.adk.async;
    requires io.github.vagran.adk.log;
    requires io.github.vagran.adk.json;
    requires io.github.vagran.adk;

    exports io.github.vagran.adk.domain;
    exports io.github.vagran.adk.domain.httpserver;
}
