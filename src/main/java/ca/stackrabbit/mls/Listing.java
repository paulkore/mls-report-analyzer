package ca.stackrabbit.mls;

import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class Listing {

    private final Set<String> fieldsRead = new HashSet<>();

    private final DateTimeFormatter df = DateTimeFormat.forPattern("MM/dd/yyyy");

    private final int index;

    public static Listing extract(int index, Element listingRoot) {
        return new Listing(index).extract(listingRoot);
    }

    private Listing(int index) {
        this.index = index;
    }

    private Listing extract(Element listingRoot) {
        System.out.println("Extracting listing No. " + index);

        Element mainSection = listingRoot.select("div.report-container > div.viewform > div.legacyBorder").first();

        extractFields(mainSection);
        extractTitleFields(mainSection);
        extractRoomDimensions(mainSection);
        extractFinalize();

        return this;
    }

    private void extractFields(Element mainSection) {
        Elements allFields = mainSection.select("span.formfield");
        for (Element field : allFields) {
            Element labelElement = field.select("label").first();
            Element valueElement = field.select("span.value").first();
            if (labelElement == null || valueElement == null) {
                continue;
            }

            String label = labelElement.text().trim().replaceAll(":", "");
            String value = valueElement.text().trim();
            if (Strings.isEmpty(label) || Strings.isEmpty(value)) {
                continue;
            }
            if (fieldsRead.contains(label)) {
                // duplicate
                continue;
            }
            fieldsRead.add(label);

            switch (label) {
                case "MLS#":
                    mlsNumber = value;
                    break;
                case "Exposure":
                    exposure = value;
                    break;
                case "List":
                    listPrice = value;
                    break;
                case "Sold":
                    soldPrice = value;
                    break;
                case "DOM":
                    daysOnMarket = Integer.parseInt(value);
                    break;
                case "Contract Date":
                    listDate = df.parseLocalDate(value);
                    break;
                case "Sold Date":
                    soldDate = df.parseLocalDate(value);
                    break;
                case "Client Remks":
                    if (value.toLowerCase().contains("corner")) {
                        cornerUnit = true;
                    }
                    break;

                default:
                    break;
            }
        }

        if (Strings.isEmpty(mlsNumber)) {
            throw new RuntimeException("Could not extract MLS number");
        }

        System.out.println("MLS#: " + mlsNumber);
    }

    private void extractTitleFields(Element mainSection) {
        Element titleSection = mainSection.select(" > div.formitem.formgroup.tabular").first();
        Elements titleFields = titleSection.select("span.value[style*=\"font-weight:bold\"]");

        this.buildingAddress = titleFields.get(0).text();
        this.unitNumber = titleFields.get(1).text();
    }

    private void extractRoomDimensions(Element mainSection) {
        // extract room dimensions
        Element roomsSection = mainSection.select(" > div.formitem.formgroup.vertical").first();
        Elements roomRows = roomsSection.select(" > div.formitem.formgroup.vertical");

        List<RoomDimensions> rooms = new ArrayList<>();

        roomsLoop:
        for (Element roomRow : roomRows) {
            Elements roomFields = roomRow.select("span.formitem > span.value");
            if (roomFields.isEmpty()) {
                continue;
            }

            String roomLengthStr = roomFields.get(3).text();
            String roomWidthStr = roomFields.get(4).text();

            double roomL = Strings.isEmpty(roomLengthStr) ? 0.0 : Double.parseDouble(roomLengthStr);
            double roomW = Strings.isEmpty(roomWidthStr) ? 0.0 : Double.parseDouble(roomWidthStr);

            boolean combined = false;
            for (int i = 5; i < roomFields.size(); i++) {
                String roomInfo = roomFields.get(i).text().toLowerCase();
                if (roomInfo.contains("combined")) {
                    combined = true;
                    break;
                }
            }

            if (combined) {
                // make sure to not double-count "combined" rooms
                // they will have the same dimensions as another room that's already registered
                for (RoomDimensions rm : rooms) {
                    if (rm.length == roomL && rm.width == roomW) {
                        continue roomsLoop;
                    }
                }
            }

            rooms.add(new RoomDimensions(roomL, roomW));
        }

        if (!rooms.isEmpty()) {
            Double size = 0.0;
            for (RoomDimensions rm : rooms) {
                size += rm.width * rm.length; // these values are in meters
            }
            size *= 10.7639; // convert to sqft.
            size *= 1.33; // offset for missing dimensions (bathroom, corridors, foyer, etc.)

            if (size > 0) {
                for (int lower = 100; lower <= 1500; lower += 100) {
                    int upper = lower + 100;
                    if (size >= lower && size < upper) {
                        sizeSqft = "" + lower + "-" + upper;
                        break;
                    }
                }
                if (Strings.isEmpty(sizeSqft)) {
                    throw new RuntimeException("Unable to convert room dimensions to unit size");
                }
            }

        }
    }

    static class RoomDimensions {
        final double length;
        final double width;
        RoomDimensions(double length, double width) {
            this.length = length;
            this.width = width;
        }
    }

    private void extractFinalize() {
        if (listPrice != null && soldPrice != null) {
            int listPriceDollars = moneyToInt(listPrice);
            int soldPriceDollars = moneyToInt(soldPrice);
            priceDifference = moneyToStr(listPriceDollars - soldPriceDollars);
        }
    }

    private String mlsNumber;
    private String buildingAddress;
    private String unitNumber;
    private String exposure;
    private Boolean cornerUnit;
    private String sizeSqft;
    private String listPrice;
    private String soldPrice;
    private String priceDifference;
    private Integer daysOnMarket;
    private LocalDate listDate;
    private LocalDate soldDate;
    private Boolean repeat;

    public final static String CSV_HEADERS = Strings.join(new String[] {
            "MLS#",
            "Address",
            "Unit#",
            "Exposure",
            "Corner",
            "Size (sqft.)",
            "List price",
            "Sold price",
            "Price diff.",
            "Days up",
            "List date",
            "Sold date",
            "Repeat",
    }, ",");

    public String csvLine() {

        String[] values = new String[] {
                safeStr(mlsNumber),
                safeStr(buildingAddress),
                safeStr(unitNumber),
                safeStr(exposure),
                boolToStr(cornerUnit),
                safeStr(sizeSqft),
                safeStr(listPrice),
                safeStr(soldPrice),
                safeStr(priceDifference),
                safeStr(daysOnMarket),
                dateToStr(listDate),
                dateToStr(soldDate),
                boolToStr(repeat),
        };

        return Strings.join(values, ",");
    }

    private static String safeStr(Object value) {
        if (value == null) {
            return "\"\"";
        }

        String valueStr = value.toString();

        boolean openQuote = valueStr.startsWith("\"");
        boolean closeQuote = valueStr.endsWith("\"");

        if (openQuote && closeQuote) {
            return valueStr;
        }
        else if (!openQuote && !closeQuote) {
            return "\"" + valueStr + "\"";
        }

        throw new RuntimeException("Unexpected quotation marks in value: " + valueStr);
    }

    private static int moneyToInt(String value) {
        return Integer.parseInt(value.replaceAll("\\$", "").replaceAll(",", ""));
    }

    private static String boolToStr(Boolean value) {
        return safeStr(value == null ? null : value ? "Y" : "N");
    }

    private static String moneyToStr(Integer value) {
        return safeStr(value == null ? null : "$" + NumberFormat.getIntegerInstance().format(value));
    }

    private static String dateToStr(LocalDate value) {
        return safeStr(value == null ? null : value.toString());
    }

    public String getPropertyAddress() {
        if (Strings.isEmpty(buildingAddress) || Strings.isEmpty(unitNumber)) {
            throw new RuntimeException("Unable to produce unique property address");
        }
        return buildingAddress + " " + unitNumber;
    }

    public void setRepeated(boolean repeat) {
        if (repeat) {
            this.repeat = true;
        }
    }

}
