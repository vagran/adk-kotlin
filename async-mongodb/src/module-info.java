module com.ast.adk.async.mongodb {
    requires kotlin.stdlib;
    requires com.ast.adk.async;
    requires kotlin.reflect;
    requires org.mongodb.driver.core;
    requires org.mongodb.bson;
    requires org.mongodb.driver.async.client;

    exports com.ast.adk.async.db.mongo;
}
