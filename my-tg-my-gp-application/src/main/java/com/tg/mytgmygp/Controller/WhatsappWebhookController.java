package com.tg.mytgmygp.Controller;

import com.tg.mytgmygp.Dto.HouseTaxDetailsRequest;
import com.tg.mytgmygp.Dto.HouseTaxSearchRequest;
import com.tg.mytgmygp.Service.DistrictService;
import com.tg.mytgmygp.Service.GramPanchayatService;
import com.tg.mytgmygp.Service.HouseTaxService;
import com.tg.mytgmygp.Service.RazorpayHouseTaxPaymentService;
import com.tg.mytgmygp.Service.MandalService;
import com.tg.mytgmygp.Service.ServiceTypeService;
import com.tg.mytgmygp.Service.WhatsappService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/v1/whatsapp")
public class WhatsappWebhookController {
    @Value("${whatsapp.webhook.verify-token}")
    private String verifyToken;

    private final WhatsappService whatsappService;
    private final DistrictService districtService;
    private final MandalService mandalService;
    private final GramPanchayatService gramPanchayatService;
    private final ObjectMapper objectMapper;
    private final ServiceTypeService serviceTypeService;
    private final HouseTaxService houseTaxService;
    private final RazorpayHouseTaxPaymentService paymentService;

    // Suitable for local testing. Use whatsapp_sessions/PostgreSQL for production persistence.
    private final Map<String, ChatSession> sessions = new ConcurrentHashMap<>();

    public WhatsappWebhookController(
            WhatsappService whatsappService,
            DistrictService districtService,
            MandalService mandalService,
            GramPanchayatService gramPanchayatService,
            ServiceTypeService serviceTypeService,
            HouseTaxService houseTaxService,
            RazorpayHouseTaxPaymentService paymentService,
            ObjectMapper objectMapper) {
        this.whatsappService = whatsappService;
        this.districtService = districtService;
        this.mandalService = mandalService;
        this.gramPanchayatService = gramPanchayatService;
        this.serviceTypeService = serviceTypeService;
        this.houseTaxService = houseTaxService;
        this.paymentService = paymentService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/webhook")
    public ResponseEntity<String> verifyWebhook(
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String token,
            @RequestParam(name = "hub.challenge", required = false) String challenge) {
        if ("subscribe".equals(mode) && verifyToken.equals(token)) {
            return ResponseEntity.ok(challenge);
        }
        return ResponseEntity.status(403).body("Verification failed");
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> receiveWebhook(@RequestBody String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode value = root.path("entry").path(0).path("changes").path(0).path("value");
            JsonNode messages = value.path("messages");

            // Meta also sends status callbacks (sent/delivered/read). They are not user messages.
            if (!messages.isArray() || messages.isEmpty()) {
                return ResponseEntity.ok("EVENT_RECEIVED");
            }

            JsonNode message = messages.get(0);
            String from = message.path("from").asString();
            String messageType = message.path("type").asString();
            ChatSession session = sessions.computeIfAbsent(from, ignored -> new ChatSession());

            if ("text".equals(messageType)) {
                String text = message.path("text").path("body").asString("").trim();
                if (text.equalsIgnoreCase("hi") || text.equalsIgnoreCase("hello")
                        || text.equalsIgnoreCase("menu") || text.equalsIgnoreCase("start")) {
                    session.reset();
                    whatsappService.sendDistrictList(from, districtService.getAllDistricts(), 0);
                } else if ("WAITING_SEARCH_VALUE".equals(session.step)) {
                    handleHouseTaxSearch(from, session, text);
                } else {
                    whatsappService.sendTextMessage(from, "Please send Hi to start or Menu to restart the service selection.");
                }
            } else if ("interactive".equals(messageType)) {
                JsonNode interactive = message.path("interactive");
                String interactiveType = interactive.path("type").asString();
                if ("list_reply".equals(interactiveType)) {
                    String selectedId = interactive.path("list_reply").path("id").asString();
                    String selectedTitle = interactive.path("list_reply").path("title").asString();
                    handleListReply(from, session, selectedId, selectedTitle);
                } else if ("button_reply".equals(interactiveType)) {
                    String buttonId = interactive.path("button_reply").path("id").asString();
                    handleButtonReply(from, session, buttonId);
                }
            }
        } catch (Exception e) {
            System.err.println("Error processing WhatsApp webhook:");
            e.printStackTrace();
        }
        return ResponseEntity.ok("EVENT_RECEIVED");
    }

    private void handleListReply(String from, ChatSession session, String selectedId, String selectedTitle) {
        if (selectedId.startsWith("DISTRICT_")) {
            session.reset();
            session.districtId = Integer.valueOf(selectedId.substring("DISTRICT_".length()));
            var mandals = mandalService.getMandalsByDistrict(session.districtId);
            whatsappService.sendMandalList(from, session.districtId, mandals, 0);
        } else if (selectedId.startsWith("MANDAL_")) {
            session.mandalId = Integer.valueOf(selectedId.substring("MANDAL_".length()));
            var mandals = mandalService.getMandalsByDistrict(session.districtId);
            boolean valid = mandals.stream().anyMatch(m -> java.util.Objects.equals(m.mandalId(), session.mandalId));
            if (!valid) {
                whatsappService.sendTextMessage(from, "That Mandal does not belong to the selected District. Please send Menu and try again.");
                session.reset();
                return;
            }
            whatsappService.sendGramPanchayatList(from, session.mandalId,
                    gramPanchayatService.getGramPanchayatsByMandal(session.mandalId), 0);
        } else if (selectedId.startsWith("GP_")) {
            session.gpId = Integer.valueOf(selectedId.substring("GP_".length()));
            var gps = gramPanchayatService.getGramPanchayatsByMandal(session.mandalId);
            boolean valid = gps.stream().anyMatch(gp -> java.util.Objects.equals(gp.gramPanchayatId(), session.gpId));
            if (!valid) {
                whatsappService.sendTextMessage(from, "That Gram Panchayat does not belong to the selected Mandal. Please send Menu and try again.");
                session.reset();
                return;
            }
            session.step = "WAITING_SERVICE";
            whatsappService.sendServiceList(from, serviceTypeService.getActiveServices());
        } else if (selectedId.startsWith("SERVICE_")) {
            Integer serviceId = Integer.valueOf(selectedId.substring("SERVICE_".length()));
            var service = serviceTypeService.getActiveServices().stream()
                    .filter(s -> java.util.Objects.equals(s.serviceId(), serviceId)).findFirst().orElse(null);
            if (service == null) {
                whatsappService.sendTextMessage(from, "That service is not available. Please send Menu to restart.");
                return;
            }
            session.serviceCode = service.serviceCode();
            if ("PROPERTY_TAX".equals(session.serviceCode)) {
                session.step = "WAITING_SEARCH_METHOD";
                whatsappService.sendHouseTaxSearchOptions(from);
            } else {
                whatsappService.sendTextMessage(from, service.serviceName() + " is selected. This service's workflow has not been implemented yet. Send Menu to select another service.");
            }
        } else if (selectedId.startsWith("PROPERTY_")) {
            Long assessmentNo = Long.valueOf(selectedId.substring("PROPERTY_".length()));
            var details = houseTaxService.getDetails(new HouseTaxDetailsRequest(
                    session.districtId, session.mandalId, session.gpId,
                    session.serviceCode, assessmentNo, session.searchMethod, session.searchValue));
            session.step = "PROPERTY_DETAILS_SHOWN";
            sendDetailsAndPaymentLink(from, session, assessmentNo, details);
        }
    }

    private void handleButtonReply(String from, ChatSession session, String buttonId) {
        if (buttonId.startsWith("DISTRICT_NEXT_")) {
            int page = Integer.parseInt(buttonId.substring("DISTRICT_NEXT_".length())) + 1;
            whatsappService.sendDistrictList(from, districtService.getAllDistricts(), page);
        } else if (buttonId.startsWith("DISTRICT_PREVIOUS_")) {
            int page = Integer.parseInt(buttonId.substring("DISTRICT_PREVIOUS_".length())) - 1;
            whatsappService.sendDistrictList(from, districtService.getAllDistricts(), page);
        } else if (buttonId.startsWith("MANDAL_NEXT_") || buttonId.startsWith("MANDAL_PREVIOUS_")) {
            boolean next = buttonId.startsWith("MANDAL_NEXT_");
            String data = buttonId.substring(next ? "MANDAL_NEXT_".length() : "MANDAL_PREVIOUS_".length());
            String[] parts = data.split("_");
            Integer districtId = Integer.valueOf(parts[0]);
            int page = Integer.parseInt(parts[1]) + (next ? 1 : -1);
            whatsappService.sendMandalList(from, districtId, mandalService.getMandalsByDistrict(districtId), page);
        } else if (buttonId.startsWith("GP_NEXT_") || buttonId.startsWith("GP_PREVIOUS_")) {
            boolean next = buttonId.startsWith("GP_NEXT_");
            String data = buttonId.substring(next ? "GP_NEXT_".length() : "GP_PREVIOUS_".length());
            String[] parts = data.split("_");
            Integer mandalId = Integer.valueOf(parts[0]);
            int page = Integer.parseInt(parts[1]) + (next ? 1 : -1);
            whatsappService.sendGramPanchayatList(from, mandalId, gramPanchayatService.getGramPanchayatsByMandal(mandalId), page);
        } else if ("SEARCH_ASSESSMENT_NO".equals(buttonId)) {
            session.searchMethod = "ASSESSMENT_NO";
            session.step = "WAITING_SEARCH_VALUE";
            whatsappService.sendTextMessage(from, "Please enter your House Tax Assessment Number (digits only).\nExample: 1704");
        } else if ("SEARCH_OWNER_NAME".equals(buttonId)) {
            session.searchMethod = "OWNER_NAME";
            session.step = "WAITING_SEARCH_VALUE";
            whatsappService.sendTextMessage(from, "Please enter the property owner's name or surname (at least 2 characters).\nExample: Vemula");
        }
    }

    private void handleHouseTaxSearch(String from, ChatSession session, String searchValue) {
        if (session.districtId == null || session.mandalId == null || session.gpId == null
                || session.serviceCode == null || session.searchMethod == null) {
            session.reset();
            whatsappService.sendTextMessage(from, "Your session is incomplete. Send Hi to start again.");
            return;
        }
        session.searchValue = searchValue;
        try {
            var response = houseTaxService.search(new HouseTaxSearchRequest(
                    session.districtId, session.mandalId, session.gpId,
                    session.serviceCode, session.searchMethod, searchValue));
            switch (response.status()) {
                case "NOT_FOUND" -> {
                    session.step = "WAITING_SEARCH_VALUE";
                    whatsappService.sendTextMessage(from, response.message() + "\nPlease try another value, or send Menu to restart.");
                }
                case "MULTIPLE_MATCHES" -> {
                    session.step = "WAITING_PROPERTY_SELECTION";
                    whatsappService.sendPropertyMatches(from, response.records());
                }
                case "SINGLE_MATCH" -> {
                    session.step = "PROPERTY_DETAILS_SHOWN";
                    Long assessmentNo = response.details().assessment().getAssessmentNo();
                    sendDetailsAndPaymentLink(from, session, assessmentNo, response.details());
                }
                default -> whatsappService.sendTextMessage(from, "Unable to process the search. Please send Menu and try again.");
            }
        } catch (IllegalArgumentException ex) {
            session.step = "WAITING_SEARCH_VALUE";
            whatsappService.sendTextMessage(from, ex.getMessage() + "\nPlease try again.");
        }
    }

    private void sendDetailsAndPaymentLink(
            String from,
            ChatSession session,
            Long assessmentNo,
            com.tg.mytgmygp.Dto.HouseTaxDetailsResponse details) {
        try {
            HouseTaxDetailsRequest request = new HouseTaxDetailsRequest(
                    session.districtId,
                    session.mandalId,
                    session.gpId,
                    session.serviceCode,
                    assessmentNo,
                    session.searchMethod,
                    session.searchValue
            );

            String paymentUrl = paymentService.createPaymentSession(from, request);
            System.out.println("House Tax payment session created for assessment: " + assessmentNo);
            whatsappService.sendHouseTaxDetails(from, details, paymentUrl);
        } catch (Exception ex) {
            System.err.println("Could not create House Tax payment session: " + ex.getMessage());
            ex.printStackTrace();
            whatsappService.sendHouseTaxDetails(from, details);
            whatsappService.sendTextMessage(
                    from,
                    "A payment link could not be created right now. Please try again later or send Menu to restart."
            );
        }
    }

    private static class ChatSession {
        private Integer districtId;
        private Integer mandalId;
        private Integer gpId;
        private String serviceCode;
        private String step;
        private String searchMethod;
        private String searchValue;

        private void reset() {
            districtId = null;
            mandalId = null;
            gpId = null;
            serviceCode = null;
            step = null;
            searchMethod = null;
            searchValue = null;
        }
    }
}
