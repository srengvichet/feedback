package kh.edu.num.feedback.web.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kh.edu.num.feedback.domain.entity.UserAccount;
import kh.edu.num.feedback.domain.repo.UserAccountRepository;
import kh.edu.num.feedback.service.TelegramService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
public class TelegramWebhookController {

    private static final Logger log = LoggerFactory.getLogger(TelegramWebhookController.class);

    private final TelegramService telegramService;
    private final UserAccountRepository userRepo;
    private final ObjectMapper mapper = new ObjectMapper();

    public TelegramWebhookController(TelegramService telegramService,
                                      UserAccountRepository userRepo) {
        this.telegramService = telegramService;
        this.userRepo = userRepo;
    }

    @PostMapping("/api/telegram/webhook")
    public ResponseEntity<String> handleUpdate(@RequestBody String body) {
        try {
            JsonNode update = mapper.readTree(body);
            JsonNode message = update.path("message");
            if (message.isMissingNode()) return ResponseEntity.ok("ok");

            Long chatId = message.path("chat").path("id").asLong();
            String text = message.path("text").asText("").trim();

            // Extract code from "/start CODE" or "/link CODE"
            String code = null;
            if (text.startsWith("/start ")) {
                code = text.substring(7).trim();
            } else if (text.startsWith("/link ")) {
                code = text.substring(6).trim();
            }

            if (code != null && !code.isEmpty()) {
                Long userId = telegramService.consumeLinkCode(code);
                if (userId != null) {
                    Optional<UserAccount> opt = userRepo.findById(userId);
                    if (opt.isPresent()) {
                        UserAccount user = opt.get();
                        user.setTelegramChatId(chatId);
                        userRepo.save(user);
                        telegramService.sendMessage(chatId,
                                "✅ Your Telegram account has been linked to *"
                                        + escapeMarkdown(user.getUsername()) + "*\\. "
                                        + "You can now use Telegram for password reset\\.",
                                "MarkdownV2");
                        log.info("Linked Telegram chatId={} to user={}", chatId, user.getUsername());
                        return ResponseEntity.ok("ok");
                    }
                }
                telegramService.sendMessage(chatId,
                        "❌ Invalid or expired link code\\. Please generate a new one from the system\\.",
                        "MarkdownV2");
            } else if (text.startsWith("/start")) {
                telegramService.sendMessage(chatId,
                        "👋 Welcome to NUM Feedback Bot\\!\n\n"
                                + "To link your account, go to the system and click *Link Telegram Account*\\.",
                        "MarkdownV2");
            }
        } catch (Exception e) {
            log.error("Error processing Telegram webhook: {}", e.getMessage());
        }
        return ResponseEntity.ok("ok");
    }

    private String escapeMarkdown(String text) {
        return text.replaceAll("([_*\\[\\]()~`>#+\\-=|{}.!])", "\\\\$1");
    }
}
