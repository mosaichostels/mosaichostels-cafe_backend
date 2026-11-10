package com.hostel.ordering.pms;

/** eZee API families: where each one lives and how it carries the credential. */
public enum EzeeFamily {
    RESERVATION_GET(Auth.QUERY, "/booking/reservation_api/listing.php"),
    CONNECTIVITY(Auth.RES_REQUEST, "/pmsinterface/pms_connectivity.php"),
    KIOSK(Auth.RES_REQUEST, "/index.php/page/service.kioskconnectivity"),
    POS2PMS(Auth.XML, "/index.php/page/service.pos2pms"),
    HOUSEKEEPING(Auth.BODY_AUTHCODE, null),
    VACATION_RENTAL(Auth.HEADER, "/channelbookings/vacation_rental.php"),
    FINANCE(Auth.BODY_AUTHCODE, null),
    GUEST_DB(Auth.BODY_AUTHKEY, "/index.php/page/service.guestdatabase");

    /** Where the credential goes: query params, RES_Request.Authentication, XML auth, body authcode, AUTH_CODE header, body authkey. */
    enum Auth { QUERY, RES_REQUEST, XML, BODY_AUTHCODE, HEADER, BODY_AUTHKEY }

    static final int DEFAULT_MAX_BYTES = 2 * 1024 * 1024;
    static final int LARGE_MAX_BYTES = 5 * 1024 * 1024;

    private static final String PAGE = "/index.php/page/";

    final Auth auth;
    private final String path;

    EzeeFamily(Auth auth, String path) {
        this.auth = auth;
        this.path = path;
    }

    /** The endpoint path for this request type; single-path families ignore it. */
    public String pathFor(String requestType) {
        return switch (this) {
            case FINANCE -> {
                if ("RetrieveBills".equals(requestType)) yield PAGE + "service.posting";
                if ("HotelExpenses".equals(requestType)) yield PAGE + "service.voucher";
                if (requestType.startsWith("XERO_")) yield PAGE + "service.PMSAccountAPI";
                throw new IllegalArgumentException("Unknown FINANCE request type");
            }
            case HOUSEKEEPING -> switch (requestType) {
                case "InhouseRoomStatus" -> PAGE + "service.hkinfoforkaterina";
                case "UpdateRoomStatus" -> PAGE + "service.hkupdatestatus";
                default -> throw new IllegalArgumentException("Unknown HOUSEKEEPING request type");
            };
            // shortcut: getdataAPI.php documents XML bodies; P0 routes the path and sends the JSON shape, P5 settles the body.
            case CONNECTIVITY -> switch (requestType) {
                case "HistoricalBookings", "Inventory", "Rate" -> "/pmsinterface/getdataAPI.php";
                default -> path;
            };
            default -> path;
        };
    }

    /** Reply size cap: the guest list and historical bookings are large, everything else small. */
    public int maxBytesFor(String requestType) {
        return this == GUEST_DB || "HistoricalBookings".equals(requestType) ? LARGE_MAX_BYTES : DEFAULT_MAX_BYTES;
    }
}
