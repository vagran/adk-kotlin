module com.ast.adk.async.mongodb {
    requires kotlin.stdlib;
    requires com.ast.adk.async;
    requires mongodb.driver.core;
    requires bson;
    requires mongodb.driver.async;
    requires kotlin.reflect;

    exports com.ast.adk.async.db.mongo;
}
