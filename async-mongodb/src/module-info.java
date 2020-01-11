module io.github.vagran.adk.async.db.mongo {
    requires kotlin.stdlib;
    requires io.github.vagran.adk.async;
    requires kotlin.reflect;
    requires org.mongodb.driver.core;
    requires org.mongodb.bson;
    requires org.mongodb.driver.async.client;
    requires transitive io.github.vagran.adk.omm;

    exports io.github.vagran.adk.async.db.mongo;
}
