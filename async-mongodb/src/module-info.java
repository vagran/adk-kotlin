module com.ast.adk.async.mongodb {
    requires kotlin.stdlib;
    requires kotlin.stdlib.jdk8;
    requires com.ast.adk.async;
    requires mongodb.driver.core;
    requires bson;
    requires mongodb.driver.async;

    exports com.ast.adk.async.db.mongo;
}
