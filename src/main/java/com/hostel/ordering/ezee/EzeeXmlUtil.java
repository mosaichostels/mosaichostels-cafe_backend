package com.hostel.ordering.ezee;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Builds/parses the flat single-level XML the eZee POS2PMS API uses for
// chargepost/voidcharge/roomlist/roomquery. Uses JDK DOM, not string
// concatenation, so field values (guest names, menu item names) are
// always correctly XML-escaped.
public final class EzeeXmlUtil {

    private EzeeXmlUtil() {}

    public static String buildRequest(LinkedHashMap<String, String> fields) {
        try {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = builder.newDocument();
            Element root = doc.createElement("request");
            doc.appendChild(root);
            for (Map.Entry<String, String> entry : fields.entrySet()) {
                Element el = doc.createElement(entry.getKey());
                el.setTextContent(entry.getValue() == null ? "" : entry.getValue());
                root.appendChild(el);
            }

            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(doc), new StreamResult(writer));

            return "<?xml version=\"1.0\" standalone=\"yes\"?>" + writer;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build eZee request XML", e);
        }
    }

    public static Map<String, String> parseFlatResponse(String xml) {
        Element root = parseRoot(xml);
        Map<String, String> result = new LinkedHashMap<>();
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) continue;
            Element el = (Element) node;
            if ("roomrows".equals(el.getTagName())) continue;
            result.put(el.getTagName(), el.getTextContent());
        }
        return result;
    }

    public static List<Map<String, String>> parseRoomRows(String xml) {
        Element root = parseRoot(xml);
        List<Map<String, String>> rows = new ArrayList<>();
        NodeList rowNodes = root.getElementsByTagName("row");
        for (int i = 0; i < rowNodes.getLength(); i++) {
            Element rowEl = (Element) rowNodes.item(i);
            Map<String, String> row = new LinkedHashMap<>();
            NodeList fields = rowEl.getChildNodes();
            for (int j = 0; j < fields.getLength(); j++) {
                Node node = fields.item(j);
                if (node.getNodeType() != Node.ELEMENT_NODE) continue;
                Element el = (Element) node;
                row.put(el.getTagName(), el.getTextContent());
            }
            rows.add(row);
        }
        return rows;
    }

    private static Element parseRoot(String xml) {
        try {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xml)));
            return doc.getDocumentElement();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse eZee response XML", e);
        }
    }
}
