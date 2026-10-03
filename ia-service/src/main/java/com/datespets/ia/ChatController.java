package com.datespets.ia;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.datespets.common.ApiException;
import com.datespets.common.Issue;
import com.datespets.common.Validacion;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final IaService ia;

    public ChatController(IaService ia) {
        this.ia = ia;
    }

    @PostMapping({"", "/"})
    public Map<String, Object> chat(@RequestBody(required = false) Map<String, Object> recibido) {
        Map<String, Object> cuerpo = Validacion.cuerpo(recibido);
        List<Issue> issues = new ArrayList<>();

        Object mensaje = cuerpo.get("mensaje");
        if (mensaje == null) {
            issues.add(Issue.invalid("mensaje", "Field required"));
        } else {
            Validacion.texto("mensaje", mensaje, 1, 2000, issues);
        }

        Object previo = cuerpo.get("previous_interaction_id");
        if (previo != null && !(previo instanceof String)) {
            issues.add(Issue.invalid("previous_interaction_id", "Input should be a valid string"));
        }

        if (!issues.isEmpty()) {
            throw Validacion.invalido(issues);
        }

        try {
            return ia.generarRespuesta((String) mensaje, (String) previo);
        } catch (ApiException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            log.error("ERROR EN CHAT IA", e);

            boolean limite = (e instanceof GeminiException g && g.getStatus() == 429)
                    || String.valueOf(e.getMessage()).contains("RESOURCE_EXHAUSTED");

            if (limite) {
                throw new ApiException(429,
                        "Se alcanzó el límite de uso de la IA. Intenta de nuevo en unos minutos.");
            }

            throw new ApiException(500, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
