package com.jarvis.app.core

import com.jarvis.app.core.router.DeterministicRouter
import com.jarvis.app.core.router.RoutingResult
import com.jarvis.app.core.tools.PhoneToolJsonParser
import com.jarvis.app.core.tools.ToolCatalog
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AgentToolTest {

    // --- PhoneToolJsonParser: on-device model output -> phone action ---

    @Test fun parsesCleanToolJson() {
        val a = PhoneToolJsonParser.parse("""{"tool":"set_volume","arguments":{"level":30}}""")
        assertNotNull(a)
        assertEquals("set_volume", a.tool)
        assertEquals(30, (a.arguments["level"] as Number).toInt())
    }

    @Test fun parsesToolJsonWrappedInProse() {
        val a = PhoneToolJsonParser.parse("""Sure! {"tool":"toggle_wifi","arguments":{"enable":true}} done""")
        assertNotNull(a)
        assertEquals("toggle_wifi", a.tool)
        assertEquals(true, a.arguments["enable"])
    }

    @Test fun rejectsUnknownTool() {
        assertNull(PhoneToolJsonParser.parse("""{"tool":"launch_missiles","arguments":{}}"""))
    }

    @Test fun rejectsNonJson() {
        assertNull(PhoneToolJsonParser.parse("I think you should turn the volume down."))
    }

    @Test fun rejectsMissingToolField() {
        assertNull(PhoneToolJsonParser.parse("""{"arguments":{"level":10}}"""))
    }

    // --- ToolCatalog: MCP inputSchema -> OpenAI tool wrapping ---

    @Test fun phoneToolsAreValidOpenAiFunctions() {
        val tools = ToolCatalog.phoneTools()
        assertTrue(tools.length() >= 8)
        val first = tools.getJSONObject(0)
        assertEquals("function", first.getString("type"))
        val fn = first.getJSONObject("function")
        assertTrue(fn.getString("name").isNotBlank())
        assertTrue(fn.getJSONObject("parameters").has("properties"))
    }

    // --- DeterministicRouter: WhatsApp routing ---

    @Test fun routesWhatsAppWithMessage() {
        val r = DeterministicRouter.route("whatsapp mom saying running late")
        assertTrue(r is RoutingResult.LocalTool)
        val a = (r as RoutingResult.LocalTool).action
        assertEquals("send_whatsapp", a.tool)
        assertEquals("mom", a.arguments["recipient"])
        assertEquals("running late", a.arguments["message"])
        assertTrue(a.confirmationRequired)
    }

    @Test fun wrapsMcpSchemaAsFunction() {
        val schema = JSONObject("""{"type":"object","properties":{"query":{"type":"string"}}}""")
        val tool = ToolCatalog.toOpenAiTool("mcp__mem__search_notes", "Search notes", schema)
        assertEquals("function", tool.getString("type"))
        assertEquals("mcp__mem__search_notes", tool.getJSONObject("function").getString("name"))
        assertEquals(schema, tool.getJSONObject("function").getJSONObject("parameters"))
    }
}
