package com.ast.adk.utils;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.config.DefaultConfiguration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;


/**
 * Logging configuration used for unit tests.
 * Note: in Java because ConsoleAppender.newBuilder() currently is not able to infer generic
 * parameters.
 */
class TestLoggingConfig extends DefaultConfiguration {

TestLoggingConfig(LoggerConfig[] loggers)
{
    setName("Unit test log");
    PatternLayout layout = PatternLayout.newBuilder()
        .withPattern(Log.TestLoggingConfiguration.PATTERN_LAYOUT).build();
    ConsoleAppender appender = ConsoleAppender.newBuilder().
        setTarget(ConsoleAppender.Target.SYSTEM_OUT).withLayout(layout).
        withName("ConsoleAppender").build();
    addAppender(appender);
    LoggerConfig root = getRootLogger();
    /* Remove all default appenders. */
    root.getAppenders().keySet().forEach(root::removeAppender);
    root.addAppender(appender, Level.DEBUG, null);
    root.setLevel(Level.DEBUG);

    for (LoggerConfig config: loggers) {
        addLogger(config.getName(), config);
    }
}

}
