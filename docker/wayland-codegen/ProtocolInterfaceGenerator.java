import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;

/**
 * Parses Wayland protocol XML files and generates a Kotlin source file
 * with wl_interface MemorySegments.
 *
 * Usage: java ProtocolInterfaceGenerator <xml-files...> <output.kt>
 */
public class ProtocolInterfaceGenerator {

    static class Arg {
        String name;
        String type;
        String iface;
    }

    static class Message {
        String name;
        List<Arg> args = new ArrayList<>();
    }

    static class WlInterface {
        String name;
        int version;
        List<Message> requests = new ArrayList<>();
        List<Message> events = new ArrayList<>();
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: java ProtocolInterfaceGenerator <xml-files...> <output.kt>");
            System.err.println("  First N arguments = Wayland protocol XML file paths");
            System.err.println("  Last argument     = output .kt file path");
            System.exit(1);
        }

        String outputPath = args[args.length - 1];
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();

        Map<String, WlInterface> interfaces = new LinkedHashMap<>();

        for (int i = 0; i < args.length - 1; i++) {
            File xmlFile = new File(args[i]);
            if (!xmlFile.exists()) {
                System.err.println("Error: XML file not found: " + args[i]);
                System.exit(1);
            }
            Document doc = builder.parse(xmlFile);
            doc.getDocumentElement().normalize();

            NodeList ifaceNodes = doc.getElementsByTagName("interface");
            for (int j = 0; j < ifaceNodes.getLength(); j++) {
                WlInterface iface = parseInterface((Element) ifaceNodes.item(j));
                interfaces.put(iface.name, iface);
            }
        }

        generateKotlin(interfaces, outputPath);
    }

    static WlInterface parseInterface(Element elem) {
        String name = elem.getAttribute("name");
        int version = Integer.parseInt(elem.getAttribute("version"));

        WlInterface iface = new WlInterface();
        iface.name = name;
        iface.version = version;

        NodeList children = elem.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) continue;
            String tag = ((Element) child).getTagName();
            if ("request".equals(tag)) {
                iface.requests.add(parseMessage((Element) child));
            } else if ("event".equals(tag)) {
                iface.events.add(parseMessage((Element) child));
            }
        }
        return iface;
    }

    static Message parseMessage(Element elem) {
        Message msg = new Message();
        msg.name = elem.getAttribute("name");

        NodeList children = elem.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) continue;
            Element e = (Element) child;
            if (!"arg".equals(e.getTagName())) continue;

            Arg arg = new Arg();
            arg.name = e.getAttribute("name");
            arg.type = e.getAttribute("type");
            String iface = e.getAttribute("interface");
            arg.iface = iface.isEmpty() ? null : iface;
            msg.args.add(arg);
        }
        return msg;
    }

    static String typeToEncoding(String type) {
        switch (type) {
            case "int":    return "i";
            case "uint":   return "u";
            case "string": return "s";
            case "object": return "o";
            case "new_id": return "n";
            case "array":  return "a";
            case "fd":     return "h";
            case "fixed":  return "f";
            default:
                System.err.println("Warning: unknown arg type '" + type + "', using '?'");
                return "?";
        }
    }

    static String buildSignature(List<Arg> args) {
        StringBuilder sig = new StringBuilder();
        for (Arg arg : args) sig.append(typeToEncoding(arg.type));
        return sig.toString();
    }

    static boolean isExternalInterface(String name) {
        return name.startsWith("wl_");
    }

    static String ifaceValName(String ifaceName) {
        return ifaceName + "_interface";
    }

    static String safeBuildName(String ifaceName) {
        return "build_" + ifaceName.replace('.', '_');
    }

    static void generateKotlin(Map<String, WlInterface> interfaces, String outputPath) throws IOException {
        Set<String> defined = interfaces.keySet();
        Set<String> externalRefs = new TreeSet<>();

        for (WlInterface iface : interfaces.values()) {
            for (Message msg : iface.requests) {
                for (Arg arg : msg.args) {
                    if (arg.iface != null && !defined.contains(arg.iface) && isExternalInterface(arg.iface)) {
                        externalRefs.add(arg.iface);
                    }
                }
            }
            for (Message msg : iface.events) {
                for (Arg arg : msg.args) {
                    if (arg.iface != null && !defined.contains(arg.iface) && isExternalInterface(arg.iface)) {
                        externalRefs.add(arg.iface);
                    }
                }
            }
        }

        StringBuilder sb = new StringBuilder();

        sb.append("package org.graphiks.kadre.ffi.wayland.generated\n\n");
        sb.append("import java.lang.foreign.*\n");
        sb.append("import java.lang.foreign.ValueLayout.*\n");
        sb.append("import java.lang.foreign.MemoryLayout.PathElement.*\n");
        sb.append("import org.graphiks.kadre.ffi.wayland.libWaylandClient\n\n");

        sb.append("// Arena.global() — wl_interface structs live for the process lifetime;\n");
        sb.append("// libwayland holds pointers to them. A scoped/auto arena would risk\n");
        sb.append("// use-after-free when the GC reclaims the arena.\n");
        sb.append("private val ARENA = Arena.global()\n\n");

        for (WlInterface iface : interfaces.values()) {
            sb.append("val ").append(ifaceValName(iface.name))
              .append(": MemorySegment by lazy { ").append(safeBuildName(iface.name)).append("() }\n");
        }
        sb.append("\n");

        if (!externalRefs.isEmpty()) {
            for (String ext : externalRefs) {
                sb.append("private val ").append(ifaceValName(ext))
                  .append(": MemorySegment by lazy {\n");
                sb.append("    val lib = libWaylandClient ?: error(\"libwayland-client.so.0 not available\")\n");
                sb.append("    lib.find(\"").append(ext).append("_interface\").orElseThrow()\n");
                sb.append("}\n");
            }
            sb.append("\n");
        }

        sb.append("private val MSG_LAYOUT = MemoryLayout.structLayout(\n");
        sb.append("    ADDRESS.withName(\"name\"), ADDRESS.withName(\"signature\"), ADDRESS.withName(\"types\"))\n");
        sb.append("    .withByteAlignment(8)\n");
        sb.append("private val IFACE_LAYOUT = MemoryLayout.structLayout(\n");
        sb.append("    ADDRESS.withName(\"name\"),\n");
        sb.append("    JAVA_INT.withName(\"version\"),\n");
        sb.append("    JAVA_INT.withName(\"method_count\"),\n");
        sb.append("    ADDRESS.withName(\"methods\").withByteAlignment(8),\n");
        sb.append("    JAVA_INT.withName(\"event_count\"),\n");
        sb.append("    MemoryLayout.paddingLayout(4),\n");
        sb.append("    ADDRESS.withName(\"events\").withByteAlignment(8))\n");
        sb.append("    .withByteAlignment(8)\n\n");

        for (WlInterface iface : interfaces.values()) {
            sb.append("private fun ").append(safeBuildName(iface.name))
              .append("(): MemorySegment = iface(\"").append(iface.name)
              .append("\", ").append(iface.version).append(", arrayOf(\n");

            for (int i = 0; i < iface.requests.size(); i++) {
                Message msg = iface.requests.get(i);
                sb.append("    msg(\"").append(msg.name).append("\", \"")
                  .append(buildSignature(msg.args)).append("\"");
                sb.append(buildTypesVarargs(msg.args, iface.name));
                sb.append(")");
                if (i < iface.requests.size() - 1) sb.append(",");
                sb.append("\n");
            }

            sb.append("), arrayOf(\n");

            for (int i = 0; i < iface.events.size(); i++) {
                Message msg = iface.events.get(i);
                sb.append("    msg(\"").append(msg.name).append("\", \"")
                  .append(buildSignature(msg.args)).append("\"");
                sb.append(buildTypesVarargs(msg.args, iface.name));
                sb.append(")");
                if (i < iface.events.size() - 1) sb.append(",");
                sb.append("\n");
            }

            sb.append("))\n\n");
        }

        sb.append("private fun msg(name: String, signature: String, vararg types: MemorySegment): MemorySegment {\n");
        sb.append("    val seg = ARENA.allocate(MSG_LAYOUT)\n");
        sb.append("    seg.set(ADDRESS, 0L, ARENA.allocateFrom(name))\n");
        sb.append("    seg.set(ADDRESS, 8L, ARENA.allocateFrom(signature))\n");
        sb.append("    if (types.isEmpty()) {\n");
        sb.append("        seg.set(ADDRESS, 16L, MemorySegment.NULL)\n");
        sb.append("    } else {\n");
        sb.append("        val arr = ARENA.allocate(ADDRESS, (types.size + 1).toLong())\n");
        sb.append("        for (i in types.indices) arr.set(ADDRESS, (i * 8).toLong(), types[i])\n");
        sb.append("        arr.set(ADDRESS, (types.size * 8).toLong(), MemorySegment.NULL)\n");
        sb.append("        seg.set(ADDRESS, 16L, arr)\n");
        sb.append("    }\n");
        sb.append("    return seg\n");
        sb.append("}\n\n");

        sb.append("private fun iface(\n");
        sb.append("    name: String, version: Int,\n");
        sb.append("    methods: Array<MemorySegment>,\n");
        sb.append("    events: Array<MemorySegment>\n");
        sb.append("): MemorySegment {\n");
        sb.append("    val seg = ARENA.allocate(IFACE_LAYOUT)\n");
        sb.append("    seg.set(ADDRESS, 0L, ARENA.allocateFrom(name))\n");
        sb.append("    seg.set(JAVA_INT, 8L, version)\n");
        sb.append("    seg.set(JAVA_INT, 12L, methods.size)\n");
        sb.append("    if (methods.isNotEmpty()) {\n");
        sb.append("        val arr = ARENA.allocate(MSG_LAYOUT, methods.size.toLong())\n");
        sb.append("        for (i in methods.indices) arr.asSlice(i * 24L).copyFrom(methods[i])\n");
        sb.append("        seg.set(ADDRESS, 16L, arr)\n");
        sb.append("    } else {\n");
        sb.append("        seg.set(ADDRESS, 16L, MemorySegment.NULL)\n");
        sb.append("    }\n");
        sb.append("    seg.set(JAVA_INT, 24L, events.size)\n");
        sb.append("    if (events.isNotEmpty()) {\n");
        sb.append("        val arr = ARENA.allocate(MSG_LAYOUT, events.size.toLong())\n");
        sb.append("        for (i in events.indices) arr.asSlice(i * 24L).copyFrom(events[i])\n");
        sb.append("        seg.set(ADDRESS, 32L, arr)\n");
        sb.append("    } else {\n");
        sb.append("        seg.set(ADDRESS, 32L, MemorySegment.NULL)\n");
        sb.append("    }\n");
        sb.append("    return seg\n");
        sb.append("}\n");

        Path outPath = Paths.get(outputPath);
        Files.createDirectories(outPath.getParent());
        Files.writeString(outPath, sb.toString());

        System.out.println("Generated: " + outputPath);
    }

    static String buildTypesVarargs(List<Arg> args, String currentIfaceName) {
        if (args.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Arg arg : args) {
            if (arg.iface != null && !arg.iface.equals(currentIfaceName)) {
                sb.append(", ").append(ifaceValName(arg.iface));
            } else {
                sb.append(", MemorySegment.NULL");
            }
        }
        return sb.toString();
    }
}
