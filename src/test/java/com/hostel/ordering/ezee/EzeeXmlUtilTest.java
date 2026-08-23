package com.hostel.ordering.ezee;

import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class EzeeXmlUtilTest {

    @Test
    void buildRequest_escapesAndOrdersFields() {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("auth", "abc123");
        fields.put("oprn", "chargepost");
        fields.put("remark", "Tea & Toast");

        String xml = EzeeXmlUtil.buildRequest(fields);

        assertTrue(xml.startsWith("<?xml version=\"1.0\" standalone=\"yes\"?>"));
        assertTrue(xml.contains("<auth>abc123</auth>"));
        assertTrue(xml.contains("<oprn>chargepost</oprn>"));
        assertTrue(xml.contains("<remark>Tea &amp; Toast</remark>"));
        assertTrue(xml.indexOf("<auth>") < xml.indexOf("<oprn>"));
    }

    @Test
    void parseFlatResponse_readsTopLevelFields() {
        String xml = "<?xml version='1.0' standalone='yes'?>"
                + "<response><status>ok</status><msg>added in queue</msg><requestid>2805</requestid></response>";

        Map<String, String> result = EzeeXmlUtil.parseFlatResponse(xml);

        assertEquals("ok", result.get("status"));
        assertEquals("added in queue", result.get("msg"));
        assertEquals("2805", result.get("requestid"));
    }

    @Test
    void parseRoomRows_readsEachRowAsFlatMap() {
        String xml = "<?xml version='1.0' standalone='yes'?><response><status>ok</status>"
                + "<roomrows>"
                + "<row><guestname>Mr. Joy</guestname><room>106</room><masterfolio>10</masterfolio><roomtype>Studio</roomtype></row>"
                + "<row><guestname>Mrs Sophia</guestname><room>109</room><masterfolio>22</masterfolio><roomtype>Single Bedroom Suite</roomtype></row>"
                + "</roomrows></response>";

        List<Map<String, String>> rows = EzeeXmlUtil.parseRoomRows(xml);

        assertEquals(2, rows.size());
        assertEquals("Mr. Joy", rows.get(0).get("guestname"));
        assertEquals("106", rows.get(0).get("room"));
        assertEquals("10", rows.get(0).get("masterfolio"));
        assertEquals("Studio", rows.get(0).get("roomtype"));
        assertEquals("Mrs Sophia", rows.get(1).get("guestname"));
    }
}
