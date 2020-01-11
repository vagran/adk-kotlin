/* Codecs for ADK classes. Placed in separate module to properly maintain dependencies. */
module com.ast.adk.json.adk_codecs {
    requires kotlin.stdlib;
    requires com.ast.adk;
    requires com.ast.adk.json;

    exports com.ast.adk.json.adk_codecs;
}
