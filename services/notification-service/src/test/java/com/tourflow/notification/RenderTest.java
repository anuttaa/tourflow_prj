package com.tourflow.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class RenderTest {
    @Test
    void substitutesPlaceholdersAndDropsUnknown() throws Exception {
        var p = new ObjectMapper().readTree("{\"ref\":\"BK-1\",\"tourTitle\":\"Стамбул\",\"status\":\"CONFIRMED\"}");
        assertEquals("Заявка BK-1: Стамбул () ", NotificationListener.render("Заявка {{ref}}: {{title}} ({{missing}}) ", p));
    }
}
