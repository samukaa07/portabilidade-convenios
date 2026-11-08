package br.com.desafio.portabilidade.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

@Service
public class AssinaturaService {

    private final String secret;

    public AssinaturaService(@Value("${central.secret}") String secret) {
        this.secret = secret;
    }

    public String assinar(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível assinar a mensagem", e);
        }
    }

    public boolean validar(String payload, String assinatura) {
        return assinatura != null && java.security.MessageDigest.isEqual(assinar(payload).getBytes(StandardCharsets.UTF_8), assinatura.getBytes(StandardCharsets.UTF_8));
    }
}
