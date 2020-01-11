/* Codecs for ADK classes. Placed in separate module to properly maintain dependencies. */
module io.github.vagran.adk.json.adk_codecs {
    requires kotlin.stdlib;
    requires io.github.vagran.adk;
    requires io.github.vagran.adk.json;

    exports io.github.vagran.adk.json.adk_codecs;
}
