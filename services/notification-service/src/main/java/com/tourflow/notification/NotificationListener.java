package com.tourflow.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tourflow.notification.model.*;
import com.tourflow.notification.repository.*;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** FR-24: РїРѕС‚СЂРµР±РёС‚РµР»СЊ СЃРѕР±С‹С‚РёР№ Kafka. РРґРµРјРїРѕС‚РµРЅС‚РµРЅ РїРѕ (event_id, recipient); РѕС€РёР±РєРё РѕС‚РїСЂР°РІРєРё РїРёС€СѓС‚СЃСЏ РІ Р¶СѓСЂРЅР°Р» Рё РЅРµ Р±Р»РѕРєРёСЂСѓСЋС‚ РїРѕС‚РѕРє СЃРѕР±С‹С‚РёР№. */
@Component
public class NotificationListener {
    private static final Logger log = LoggerFactory.getLogger(NotificationListener.class);
    private final ObjectMapper mapper; private final TemplateRepository templates; private final NotificationRepository notifications;
    private final ContactRepository contacts; private final JavaMailSender mail; private final String from;

    public NotificationListener(ObjectMapper mapper, TemplateRepository templates, NotificationRepository notifications, ContactRepository contacts,
                                JavaMailSender mail, @Value("${app.mail-from}") String from) {
        this.mapper = mapper; this.templates = templates; this.notifications = notifications; this.contacts = contacts; this.mail = mail; this.from = from;
    }

    @KafkaListener(topics = {"user.registered", "user.blocked", "tour.published", "tour.validation.failed", "booking.created",
            "booking.status.changed", "booking.cancelled"}, groupId = "notification")
    @Transactional
    public void on(String message) {
        try { handle(mapper.readTree(message)); }
        catch (Exception e) { log.error("РќРµ СѓРґР°Р»РѕСЃСЊ РѕР±СЂР°Р±РѕС‚Р°С‚СЊ СЃРѕР±С‹С‚РёРµ: {}", e.toString()); }
    }

    private static String txt(JsonNode n, String f) { return n.hasNonNull(f) ? n.get(f).asText() : ""; }

    void handle(JsonNode env) {
        String eventId = txt(env, "eventId"), type = txt(env, "eventType");
        JsonNode p = env.get("payload");
        if (p == null) return;
        String recipient;
        switch (type) {
            case "user.registered" -> {
                recipient = txt(p, "email");
                if (p.hasNonNull("userId")) {
                    Contact c = contacts.findById(UUID.fromString(p.get("userId").asText())).orElseGet(Contact::new);
                    c.setUserRef(UUID.fromString(p.get("userId").asText())); c.setEmail(recipient); c.setFirstName(txt(p, "firstName")); c.setUpdatedAt(Instant.now());
                    contacts.save(c);
                }
            }
            case "user.blocked" -> recipient = txt(p, "email");
            case "tour.published", "tour.validation.failed" -> recipient = txt(p, "managerEmail");
            case "booking.status.changed" -> {
                if ("CANCELLED".equals(txt(p, "status"))) return; // РїРѕ РѕС‚РјРµРЅРµ СѓС…РѕРґРёС‚ booking.cancelled
                recipient = recipientFor(p);
            }
            default -> recipient = recipientFor(p);
        }
        if (recipient.isBlank() || recipient.equals("null")) { log.warn("РќРµС‚ Р°РґСЂРµСЃР°С‚Р° РґР»СЏ {}", type); return; }
        if (notifications.existsByEventIdAndRecipient(eventId, recipient)) return;

        String code = type.equals("booking.status.changed") ? type + ":" + txt(p, "status") : type;
        Template t = templates.findByCodeAndActiveTrue(code).or(() -> templates.findByCodeAndActiveTrue(type)).orElse(null);
        if (t == null) return;
        String subject = render(t.getSubjectTpl(), p), body = render(t.getBodyTpl(), p);
        Notification n = new Notification();
        n.setTemplateId(t.getId()); n.setEventId(eventId); n.setEventType(type); n.setRecipient(recipient); n.setSubject(subject); n.setBody(body);
        try {
            SimpleMailMessage m = new SimpleMailMessage();
            m.setFrom(from); m.setTo(recipient); m.setSubject(subject); m.setText(body);
            mail.send(m);
            n.setStatus("SENT");
        } catch (Exception e) {
            n.setStatus("FAILED"); n.setError(e.getMessage() == null ? e.toString() : e.getMessage().substring(0, Math.min(400, e.getMessage().length())));
            log.warn("РџРёСЃСЊРјРѕ РґР»СЏ {} РЅРµ РѕС‚РїСЂР°РІР»РµРЅРѕ: {}", recipient, e.toString());
        }
        notifications.save(n);
    }

    /** РђРґСЂРµСЃ РёР· СЃРѕР±С‹С‚РёСЏ, РёРЅР°С‡Рµ РёР· РєРѕРїРёРё РєРѕРЅС‚Р°РєС‚РѕРІ (user_contacts). */
    private String recipientFor(JsonNode p) {
        String email = txt(p, "userEmail");
        if (!email.isBlank() && !email.equals("null")) return email;
        if (p.hasNonNull("userId")) return contacts.findById(UUID.fromString(p.get("userId").asText())).map(Contact::getEmail).orElse("");
        return "";
    }

    static String render(String tpl, JsonNode payload) {
        String out = tpl;
        Iterator<Map.Entry<String, JsonNode>> it = payload.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            out = out.replace("{{" + e.getKey() + "}}", e.getValue().asText());
        }
        // СЃРѕРІРјРµСЃС‚РёРјРѕСЃС‚СЊ С€Р°Р±Р»РѕРЅРѕРІ: {{title}} РґР»СЏ tour.* Рё {{title}} РІ booking.* Р±РµСЂС‘С‚СЃСЏ РёР· tourTitle
        if (payload.hasNonNull("tourTitle")) out = out.replace("{{title}}", payload.get("tourTitle").asText());
        return out.replaceAll("\\{\\{[a-zA-Z]+}}", "");
    }
}

