package kh.edu.num.feedback.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import kh.edu.num.feedback.config.TelegramProperties;
import kh.edu.num.feedback.domain.entity.UserAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TelegramService {

    private static final Logger log = LoggerFactory.getLogger(TelegramService.class);
    private static final int OTP_TTL_SECONDS = 300;   // 5 minutes
    private static final int LINK_TTL_SECONDS = 600;  // 10 minutes

    private final TelegramProperties props;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();
    private final SecureRandom rng = new SecureRandom();

    /** chatId -> OtpEntry */
    private final ConcurrentHashMap<Long, OtpEntry> otpStore = new ConcurrentHashMap<>();
    /** linkCode -> LinkEntry */
    private final ConcurrentHashMap<String, LinkEntry> linkStore = new ConcurrentHashMap<>();

    public TelegramService(TelegramProperties props) {
        this.props = props;
    }

    // ─── OTP for password reset ──────────────────────────────────────────────

    /**
     * Send a 6-digit OTP to the user's linked Telegram account.
     * @return true if sent, false if Telegram not linked or bot disabled
     */
    public boolean sendOtp(UserAccount user) {
        if (!props.isEnabled()) return false;
        Long chatId = user.getTelegramChatId();
        if (chatId == null) return false;

        String otp = String.format("%06d", rng.nextInt(1_000_000));
        otpStore.put(chatId, new OtpEntry(otp, Instant.now().plusSeconds(OTP_TTL_SECONDS)));

        String text = "🔐 *NUM Feedback — Password Reset*\n\n"
                + "Your one\\-time code is:\n\n"
                + "`" + otp + "`\n\n"
                + "Valid for *5 minutes*\\. Do not share this code\\.";
        sendMessage(chatId, text, "MarkdownV2");
        return true;
    }

    /**
     * Validate the OTP for the given chatId. Consumes it on success.
     */
    public boolean validateOtp(Long chatId, String input) {
        OtpEntry entry = otpStore.get(chatId);
        if (entry == null || Instant.now().isAfter(entry.expiresAt())) {
            otpStore.remove(chatId);
            return false;
        }
        if (entry.otp().equals(input.trim())) {
            otpStore.remove(chatId);
            return true;
        }
        return false;
    }

    // ─── Account linking ─────────────────────────────────────────────────────

    /**
     * Generate a short link code so the user can connect their Telegram account.
     */
    public String generateLinkCode(Long userId) {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) sb.append(chars.charAt(rng.nextInt(chars.length())));
        String code = sb.toString();
        linkStore.put(code, new LinkEntry(userId, Instant.now().plusSeconds(LINK_TTL_SECONDS)));
        return code;
    }

    /**
     * Called by the webhook when a user sends /start CODE or /link CODE.
     * @return the userId the code belongs to, or null if invalid/expired
     */
    public Long consumeLinkCode(String code) {
        LinkEntry entry = linkStore.remove(code);
        if (entry == null || Instant.now().isAfter(entry.expiresAt())) return null;
        return entry.userId();
    }

    // ─── Webhook registration ────────────────────────────────────────────────

    public String registerWebhook(String url) {
        if (!props.isEnabled() || props.getBotToken().isBlank()) return "bot_disabled";
        try {
            String apiUrl = "https://api.telegram.org/bot" + props.getBotToken() + "/setWebhook";
            String body = mapper.writeValueAsString(Map.of("url", url));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            log.info("setWebhook response: {}", resp.body());
            return resp.body();
        } catch (Exception e) {
            log.error("registerWebhook failed: {}", e.getMessage());
            return "error: " + e.getMessage();
        }
    }

    // ─── Internal send ───────────────────────────────────────────────────────

    @Async
    public void sendMessage(Long chatId, String text, String parseMode) {
        if (!props.isEnabled() || props.getBotToken().isBlank()) return;
        try {
            String apiUrl = "https://api.telegram.org/bot" + props.getBotToken() + "/sendMessage";
            Map<String, Object> payload = parseMode != null
                    ? Map.of("chat_id", chatId, "text", text, "parse_mode", parseMode)
                    : Map.of("chat_id", chatId, "text", text);
            String body = mapper.writeValueAsString(payload);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            log.error("sendMessage to chatId={} failed: {}", chatId, e.getMessage());
        }
    }

    public boolean isEnabled() { return props.isEnabled(); }
    public String getBotUsername() { return props.getBotUsername(); }

    // ─── Inner records ───────────────────────────────────────────────────────

    private record OtpEntry(String otp, Instant expiresAt) {}
    private record LinkEntry(Long userId, Instant expiresAt) {}
}
