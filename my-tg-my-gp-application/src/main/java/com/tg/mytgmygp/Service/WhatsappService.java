package com.tg.mytgmygp.Service;

import com.tg.mytgmygp.Dto.DistrictResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class WhatsappService {

    @Value("${whatsapp.api.version}")
    private String apiVersion;

    @Value("${whatsapp.base.url}")
    private String baseUrl;

    @Value("${whatsapp.phone.number.id}")
    private String phoneNumberId;

    @Value("${whatsapp.access.token}")
    private String accessToken;

    private final RestClient restClient = RestClient.builder().build();

    public void sendTextMessage(
            String recipientPhoneNumber,
            String message) {

        String url = baseUrl
                + "/" + apiVersion
                + "/" + phoneNumberId
                + "/messages";

        Map<String, Object> requestBody = Map.of(
                "messaging_product", "whatsapp",
                "recipient_type", "individual",
                "to", recipientPhoneNumber,
                "type", "text",
                "text", Map.of(
                        "preview_url", false,
                        "body", message
                )
        );

        restClient.post()
                .uri(url)
                .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + accessToken
                )
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .toBodilessEntity();

        System.out.println(
                "WhatsApp message sent to: "
                        + recipientPhoneNumber
        );
    }

    public void sendDistrictList(
            String recipientPhoneNumber,
            List<DistrictResponse> districts,
            int page) {

        final int PAGE_SIZE = 10;

        // No districts
        if (districts == null || districts.isEmpty()) {
            sendTextMessage(
                    recipientPhoneNumber,
                    "No districts are available."
            );
            return;
        }

        // Calculate total pages dynamically
        int totalDistricts = districts.size();
        int totalPages = (totalDistricts + PAGE_SIZE - 1) / PAGE_SIZE;

        // Make sure page is within valid range
        if (page < 0) {
            page = 0;
        }

        if (page >= totalPages) {
            page = totalPages - 1;
        }

        // Calculate start and end indexes
        int startIndex = page * PAGE_SIZE;
        int endIndex = Math.min(
                startIndex + PAGE_SIZE,
                totalDistricts
        );

        // Get only current page's districts
        List<DistrictResponse> pageDistricts =
                districts.subList(startIndex, endIndex);

        // Create WhatsApp list rows
        List<Map<String, Object>> rows = pageDistricts.stream()
                .map(district -> Map.<String, Object>of(
                        "id", "DISTRICT_" + district.districtId(),
                        "title", district.districtName()
                ))
                .toList();

        String url = baseUrl
                + "/"
                + apiVersion
                + "/"
                + phoneNumberId
                + "/messages";

        // Page information
        String pageText =
                "Showing districts "
                        + (startIndex + 1)
                        + "-"
                        + endIndex
                        + " of "
                        + totalDistricts
                        + "\n\n"
                        + "Please select your District:";

        Map<String, Object> requestBody = Map.of(
                "messaging_product", "whatsapp",
                "recipient_type", "individual",
                "to", recipientPhoneNumber,
                "type", "interactive",
                "interactive", Map.of(
                        "type", "list",

                        "header", Map.of(
                                "type", "text",
                                "text", "MY TG / MY GP"
                        ),

                        "body", Map.of(
                                "text", pageText
                        ),

                        "footer", Map.of(
                                "text",
                                "Page "
                                        + (page + 1)
                                        + " of "
                                        + totalPages
                        ),

                        "action", Map.of(
                                "button", "Select District",
                                "sections", List.of(
                                        Map.of(
                                                "title",
                                                "Telangana Districts",
                                                "rows",
                                                rows
                                        )
                                )
                        )
                )
        );

        // Send district list
        restClient.post()
                .uri(url)
                .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + accessToken
                )
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .toBodilessEntity();

        System.out.println(
                "District page "
                        + (page + 1)
                        + "/"
                        + totalPages
                        + " sent to: "
                        + recipientPhoneNumber
        );

        // Send navigation buttons if required
        sendDistrictNavigation(
                recipientPhoneNumber,
                page,
                totalPages
        );
    }

    private void sendDistrictNavigation(
            String recipientPhoneNumber,
            int currentPage,
            int totalPages) {

        // No navigation required if there is only one page
        if (totalPages <= 1) {
            return;
        }

        String url = baseUrl
                + "/"
                + apiVersion
                + "/"
                + phoneNumberId
                + "/messages";

        List<Map<String, Object>> buttons = new java.util.ArrayList<>();

        // Previous button
        if (currentPage > 0) {

            buttons.add(
                    Map.of(
                            "type", "reply",
                            "reply", Map.of(
                                    "id",
                                    "DISTRICT_PREVIOUS_"
                                            + currentPage,

                                    "title",
                                    "Previous 10"
                            )
                    )
            );
        }

        // Next button
        if (currentPage < totalPages - 1) {

            buttons.add(
                    Map.of(
                            "type", "reply",
                            "reply", Map.of(
                                    "id",
                                    "DISTRICT_NEXT_"
                                            + currentPage,

                                    "title",
                                    "Next 10"
                            )
                    )
            );
        }

        Map<String, Object> requestBody = Map.of(
                "messaging_product", "whatsapp",
                "recipient_type", "individual",
                "to", recipientPhoneNumber,
                "type", "interactive",

                "interactive", Map.of(
                        "type", "button",

                        "body", Map.of(
                                "text",
                                "Page "
                                        + (currentPage + 1)
                                        + " of "
                                        + totalPages
                                        + ". Use the buttons below to navigate."
                        ),

                        "action", Map.of(
                                "buttons", buttons
                        )
                )
        );

        restClient.post()
                .uri(url)
                .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + accessToken
                )
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .toBodilessEntity();

        System.out.println(
                "District navigation sent. "
                        + "Current page: "
                        + (currentPage + 1)
                        + "/"
                        + totalPages
        );
    }

    public void sendMandalList(
            String recipientPhoneNumber,
            Integer districtId,
            List<com.tg.mytgmygp.Dto.MandalResponse> mandals,
            int page) {

        final int PAGE_SIZE = 10;

        if (mandals == null || mandals.isEmpty()) {

            sendTextMessage(
                    recipientPhoneNumber,
                    "No mandals are available for the selected district."
            );

            return;
        }

        int totalMandals = mandals.size();

        int totalPages =
                (totalMandals + PAGE_SIZE - 1) / PAGE_SIZE;

        if (page < 0) {
            page = 0;
        }

        if (page >= totalPages) {
            page = totalPages - 1;
        }

        int startIndex = page * PAGE_SIZE;

        int endIndex =
                Math.min(startIndex + PAGE_SIZE, totalMandals);

        List<com.tg.mytgmygp.Dto.MandalResponse> pageMandals =
                mandals.subList(startIndex, endIndex);

        List<Map<String, Object>> rows =
                pageMandals.stream()
                        .map(mandal -> Map.<String, Object>of(
                                "id",
                                "MANDAL_" + mandal.mandalId(),

                                "title",
                                mandal.mandalName()
                        ))
                        .toList();

        String url =
                baseUrl
                        + "/"
                        + apiVersion
                        + "/"
                        + phoneNumberId
                        + "/messages";

        String bodyText =
                "Showing mandals "
                        + (startIndex + 1)
                        + "-"
                        + endIndex
                        + " of "
                        + totalMandals
                        + "\n\n"
                        + "Please select your Mandal:";

        Map<String, Object> requestBody = Map.of(
                "messaging_product", "whatsapp",
                "recipient_type", "individual",
                "to", recipientPhoneNumber,
                "type", "interactive",

                "interactive", Map.of(
                        "type", "list",

                        "header", Map.of(
                                "type", "text",
                                "text", "MY TG / MY GP"
                        ),

                        "body", Map.of(
                                "text", bodyText
                        ),

                        "footer", Map.of(
                                "text",
                                "Page "
                                        + (page + 1)
                                        + " of "
                                        + totalPages
                        ),

                        "action", Map.of(
                                "button", "Select Mandal",

                                "sections", List.of(
                                        Map.of(
                                                "title",
                                                "Available Mandals",

                                                "rows",
                                                rows
                                        )
                                )
                        )
                )
        );

        restClient.post()
                .uri(url)
                .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + accessToken
                )
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .toBodilessEntity();

        System.out.println(
                "Mandal page "
                        + (page + 1)
                        + "/"
                        + totalPages
                        + " sent for district "
                        + districtId
        );

        sendMandalNavigation(
                recipientPhoneNumber,
                districtId,
                page,
                totalPages
        );
    }
    private void sendMandalNavigation(
            String recipientPhoneNumber,
            Integer districtId,
            int currentPage,
            int totalPages) {

        if (totalPages <= 1) {
            return;
        }

        String url =
                baseUrl
                        + "/"
                        + apiVersion
                        + "/"
                        + phoneNumberId
                        + "/messages";

        List<Map<String, Object>> buttons =
                new java.util.ArrayList<>();

        if (currentPage > 0) {

            buttons.add(
                    Map.of(
                            "type", "reply",
                            "reply", Map.of(
                                    "id",
                                    "MANDAL_PREVIOUS_"
                                            + districtId
                                            + "_"
                                            + currentPage,

                                    "title",
                                    "Previous 10"
                            )
                    )
            );
        }

        if (currentPage < totalPages - 1) {

            buttons.add(
                    Map.of(
                            "type", "reply",
                            "reply", Map.of(
                                    "id",
                                    "MANDAL_NEXT_"
                                            + districtId
                                            + "_"
                                            + currentPage,

                                    "title",
                                    "Next 10"
                            )
                    )
            );
        }

        Map<String, Object> requestBody =
                Map.of(
                        "messaging_product",
                        "whatsapp",

                        "recipient_type",
                        "individual",

                        "to",
                        recipientPhoneNumber,

                        "type",
                        "interactive",

                        "interactive",
                        Map.of(
                                "type",
                                "button",

                                "body",
                                Map.of(
                                        "text",
                                        "Page "
                                                + (currentPage + 1)
                                                + " of "
                                                + totalPages
                                                + ". Use the buttons below."
                                ),

                                "action",
                                Map.of(
                                        "buttons",
                                        buttons
                                )
                        )
                );

        restClient.post()
                .uri(url)
                .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + accessToken
                )
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .toBodilessEntity();
    }

    public void sendGramPanchayatList(
            String recipientPhoneNumber,
            Integer mandalId,
            List<com.tg.mytgmygp.Dto.GramPanchayatResponse> gramPanchayats,
            int page) {

        final int PAGE_SIZE = 10;

        if (gramPanchayats == null || gramPanchayats.isEmpty()) {

            sendTextMessage(
                    recipientPhoneNumber,
                    "No Gram Panchayats are available for the selected Mandal."
            );

            return;
        }

        int totalGPs = gramPanchayats.size();

        int totalPages =
                (totalGPs + PAGE_SIZE - 1) / PAGE_SIZE;

        if (page < 0) {
            page = 0;
        }

        if (page >= totalPages) {
            page = totalPages - 1;
        }

        int startIndex = page * PAGE_SIZE;

        int endIndex =
                Math.min(startIndex + PAGE_SIZE, totalGPs);

        List<com.tg.mytgmygp.Dto.GramPanchayatResponse> pageGPs =
                gramPanchayats.subList(startIndex, endIndex);

        List<Map<String, Object>> rows =
                pageGPs.stream()
                        .map(gp -> Map.<String, Object>of(
                                "id",
                                "GP_" + gp.gramPanchayatId(),

                                "title",
                                gp.gramPanchayatName()
                        ))
                        .toList();

        String url =
                baseUrl
                        + "/"
                        + apiVersion
                        + "/"
                        + phoneNumberId
                        + "/messages";

        String bodyText =
                "Showing Gram Panchayats "
                        + (startIndex + 1)
                        + "-"
                        + endIndex
                        + " of "
                        + totalGPs
                        + "\n\n"
                        + "Please select your Gram Panchayat:";

        Map<String, Object> requestBody =
                Map.of(
                        "messaging_product",
                        "whatsapp",

                        "recipient_type",
                        "individual",

                        "to",
                        recipientPhoneNumber,

                        "type",
                        "interactive",

                        "interactive",
                        Map.of(
                                "type",
                                "list",

                                "header",
                                Map.of(
                                        "type",
                                        "text",
                                        "text",
                                        "MY TG / MY GP"
                                ),

                                "body",
                                Map.of(
                                        "text",
                                        bodyText
                                ),

                                "footer",
                                Map.of(
                                        "text",
                                        "Page "
                                                + (page + 1)
                                                + " of "
                                                + totalPages
                                ),

                                "action",
                                Map.of(
                                        "button",
                                        "Select GP",

                                        "sections",
                                        List.of(
                                                Map.of(
                                                        "title",
                                                        "Gram Panchayats",

                                                        "rows",
                                                        rows
                                                )
                                        )
                                )
                        )
                );

        restClient.post()
                .uri(url)
                .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + accessToken
                )
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .toBodilessEntity();

        System.out.println(
                "GP page "
                        + (page + 1)
                        + "/"
                        + totalPages
                        + " sent for mandal "
                        + mandalId
        );

        sendGramPanchayatNavigation(
                recipientPhoneNumber,
                mandalId,
                page,
                totalPages
        );
    }

    private void sendGramPanchayatNavigation(
            String recipientPhoneNumber,
            Integer mandalId,
            int currentPage,
            int totalPages) {

        if (totalPages <= 1) {
            return;
        }

        String url =
                baseUrl
                        + "/"
                        + apiVersion
                        + "/"
                        + phoneNumberId
                        + "/messages";

        List<Map<String, Object>> buttons =
                new java.util.ArrayList<>();

        if (currentPage > 0) {

            buttons.add(
                    Map.of(
                            "type",
                            "reply",

                            "reply",
                            Map.of(
                                    "id",
                                    "GP_PREVIOUS_"
                                            + mandalId
                                            + "_"
                                            + currentPage,

                                    "title",
                                    "Previous 10"
                            )
                    )
            );
        }

        if (currentPage < totalPages - 1) {

            buttons.add(
                    Map.of(
                            "type",
                            "reply",

                            "reply",
                            Map.of(
                                    "id",
                                    "GP_NEXT_"
                                            + mandalId
                                            + "_"
                                            + currentPage,

                                    "title",
                                    "Next 10"
                            )
                    )
            );
        }

        Map<String, Object> requestBody =
                Map.of(
                        "messaging_product",
                        "whatsapp",

                        "recipient_type",
                        "individual",

                        "to",
                        recipientPhoneNumber,

                        "type",
                        "interactive",

                        "interactive",
                        Map.of(
                                "type",
                                "button",

                                "body",
                                Map.of(
                                        "text",
                                        "Page "
                                                + (currentPage + 1)
                                                + " of "
                                                + totalPages
                                ),

                                "action",
                                Map.of(
                                        "buttons",
                                        buttons
                                )
                        )
                );

        restClient.post()
                .uri(url)
                .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + accessToken
                )
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .toBodilessEntity();
    }

    public void sendServiceList(
            String recipientPhoneNumber,
            List<com.tg.mytgmygp.Dto.ServiceTypeResponse> services) {

        if (services == null || services.isEmpty()) {
            sendTextMessage(recipientPhoneNumber, "No services are currently available.");
            return;
        }

        List<Map<String, Object>> rows = services.stream()
                .map(service -> {
                    String title = switch (service.serviceCode()) {
                        case "BUILDING_PERMISSION" -> "Building Permission";
                        case "LAYOUT_PERMISSION" -> "Layout Permission";
                        case "MUTATION" -> "Property Mutation";
                        case "PROPERTY_TAX" -> "House Tax";
                        case "TRADE_LICENSE_FRESH" -> "New Trade License";
                        case "TRADE_LICENSE_RENEWAL" -> "Renew Trade License";
                        default -> shorten(service.serviceName(), 24);
                    };
                    return Map.<String, Object>of(
                            "id", "SERVICE_" + service.serviceId(),
                            "title", title,
                            "description", shorten(service.serviceName(), 72)
                    );
                })
                .toList();

        Map<String, Object> requestBody = Map.of(
                "messaging_product", "whatsapp",
                "recipient_type", "individual",
                "to", recipientPhoneNumber,
                "type", "interactive",
                "interactive", Map.of(
                        "type", "list",
                        "header", Map.of("type", "text", "text", "MY TG / MY GP"),
                        "body", Map.of("text", "Please select a service:"),
                        "footer", Map.of("text", "Available Services"),
                        "action", Map.of(
                                "button", "Select Service",
                                "sections", List.of(Map.of("title", "Services", "rows", rows))
                        )
                )
        );
        sendInteractiveRequest(recipientPhoneNumber, requestBody);
        System.out.println("Service list sent to: " + recipientPhoneNumber);
    }

    public void sendHouseTaxSearchOptions(String recipientPhoneNumber) {
        Map<String, Object> requestBody = Map.of(
                "messaging_product", "whatsapp",
                "recipient_type", "individual",
                "to", recipientPhoneNumber,
                "type", "interactive",
                "interactive", Map.of(
                        "type", "button",
                        "body", Map.of("text", "House Tax selected. How would you like to find your property?"),
                        "action", Map.of("buttons", List.of(
                                Map.of("type", "reply", "reply", Map.of("id", "SEARCH_ASSESSMENT_NO", "title", "Assessment Number")),
                                Map.of("type", "reply", "reply", Map.of("id", "SEARCH_OWNER_NAME", "title", "Owner Name"))
                        ))
                )
        );
        sendInteractiveRequest(recipientPhoneNumber, requestBody);
    }

    public void sendPropertyMatches(
            String recipientPhoneNumber,
            List<com.tg.mytgmygp.Dto.HouseTaxSearchItem> records) {
        if (records == null || records.isEmpty()) {
            sendTextMessage(recipientPhoneNumber, "No matching properties were found. Please try again with another value.");
            return;
        }
        List<Map<String, Object>> rows = records.stream().map(record -> {
            String title = shorten(record.owner() == null ? "Property" : record.owner(), 24);
            String description = "Assessment: " + record.assessmentNo()
                    + (record.houseNo() == null ? "" : " | House: " + record.houseNo());
            return Map.<String, Object>of(
                    "id", "PROPERTY_" + record.assessmentNo(),
                    "title", title,
                    "description", shorten(description, 72)
            );
        }).toList();

        Map<String, Object> requestBody = Map.of(
                "messaging_product", "whatsapp",
                "recipient_type", "individual",
                "to", recipientPhoneNumber,
                "type", "interactive",
                "interactive", Map.of(
                        "type", "list",
                        "header", Map.of("type", "text", "text", "House Tax"),
                        "body", Map.of("text", "Multiple properties found. Select your property:"),
                        "footer", Map.of("text", "Select the correct property"),
                        "action", Map.of("button", "Select Property", "sections",
                                List.of(Map.of("title", "Matching Properties", "rows", rows)))
                )
        );
        sendInteractiveRequest(recipientPhoneNumber, requestBody);
    }

    public void sendHouseTaxDetails(
            String recipientPhoneNumber,
            com.tg.mytgmygp.Dto.HouseTaxDetailsResponse details) {
        sendHouseTaxDetails(recipientPhoneNumber, details, null);
    }

    public void sendHouseTaxDetails(
            String recipientPhoneNumber,
            com.tg.mytgmygp.Dto.HouseTaxDetailsResponse details,
            String paymentUrl) {

        var assessment = details.assessment();
        var current = details.currentDemand();
        var arrear = details.arrearDetails();

        StringBuilder message = new StringBuilder();
        message.append("*HOUSE TAX - PROPERTY DETAILS*\n\n")
                .append("*Owner:* ").append(safe(assessment.getOwner())).append("\n")
                .append("*Assessment No.:* ").append(assessment.getAssessmentNo()).append("\n")
                .append("*PTIN:* ").append(safe(assessment.getPtin())).append("\n")
                .append("*House No.:* ").append(safe(assessment.getHouseNo())).append("\n")
                .append("*Father/Husband:* ").append(safe(assessment.getFatherHusbandName())).append("\n")
                .append("*Usage:* ").append(safe(assessment.getBuildingUsage())).append("\n")
                .append("*Building Classification:* ").append(safe(assessment.getBuildingClassification())).append("\n")
                .append("*Assessment Date:* ").append(formatDate(assessment.getDtOfAsmt())).append("\n")
                .append("*Exemption:* ").append(safe(assessment.getExemption())).append("\n")
                .append("*Court Case:* ").append(safe(assessment.getCourtCase())).append("\n\n")
                .append("*Location*\n")
                .append("District: ").append(safe(details.districtName())).append("\n")
                .append("Mandal: ").append(safe(details.mandalName())).append("\n")
                .append("Gram Panchayat: ").append(safe(details.gramPanchayatName())).append("\n\n");

        message.append("*CURRENT DEMAND*\n");
        if (current == null) {
            message.append("No current demand record found.\n");
        } else {
            message.append("Tax Period: ")
                    .append(formatDate(current.getPeriodFrom()))
                    .append(" to ")
                    .append(formatDate(current.getPeriodTo())).append("\n")
                    .append("House Tax: ₹").append(money(current.getHouseTax())).append("\n")
                    .append("Library Cess: ₹").append(money(current.getLibraryCess())).append("\n")
                    .append("Drainage Tax: ₹").append(money(current.getDrainageTax())).append("\n")
                    .append("Water Tax: ₹").append(money(current.getWaterTax())).append("\n")
                    .append("Lighting Tax: ₹").append(money(current.getLightingTax())).append("\n")
                    .append("*Current Demand Total: ₹").append(money(current.getTotalAmount())).append("*\n");
        }

        message.append("\n*ARREARS*\n");
        if (arrear == null) {
            message.append("No arrears recorded.\n")
                    .append("Arrears Total: ₹0.00\n");
        } else {
            if (arrear.getPeriodFrom() != null || arrear.getPeriodTo() != null) {
                message.append("Period: ")
                        .append(formatDate(arrear.getPeriodFrom()))
                        .append(" to ")
                        .append(formatDate(arrear.getPeriodTo())).append("\n");
            }
            message.append("House Tax: ₹").append(money(arrear.getHouseTax())).append("\n")
                    .append("Library Cess: ₹").append(money(arrear.getLibraryCess())).append("\n")
                    .append("Drainage Tax: ₹").append(money(arrear.getDrainageTax())).append("\n")
                    .append("Water Tax: ₹").append(money(arrear.getWaterTax())).append("\n")
                    .append("Lighting Tax: ₹").append(money(arrear.getLightingTax())).append("\n")
                    .append("*Arrears Total: ₹").append(money(arrear.getTotalAmount())).append("*\n");
        }

        message.append("\n*TOTAL PAYABLE: ₹")
                .append(money(details.totalPayable()))
                .append("*\n\n")
                .append("Please verify the property and tax details before proceeding.");

        String detailsMessage = message.toString();
        if (paymentUrl != null && !paymentUrl.isBlank()) {
            String messageWithPayment = detailsMessage
                    + "\n\nComplete payment securely:\n"
                    + paymentUrl
                    + "\n\nYour receipt will be available after payment is verified.";
            sendTextMessage(recipientPhoneNumber, messageWithPayment);
        } else {
            sendTextMessage(recipientPhoneNumber, detailsMessage);
        }
    }

    private String money(java.math.BigDecimal amount) {
        return amount == null ? "0.00" : amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private String formatDate(java.time.LocalDate date) {
        return date == null ? "Not available" : date.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy"));
    }

    private void sendInteractiveRequest(String recipientPhoneNumber, Map<String, Object> requestBody) {
        String url = baseUrl + "/" + apiVersion + "/" + phoneNumberId + "/messages";
        restClient.post().uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .toBodilessEntity();
    }

    private String shorten(String value, int maxLength) {
        if (value == null) return "";
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "Not provided" : value;
    }
}
