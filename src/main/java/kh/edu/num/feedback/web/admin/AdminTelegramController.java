package kh.edu.num.feedback.web.admin;

import kh.edu.num.feedback.service.TelegramService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AdminTelegramController {

    private final TelegramService telegramService;

    @Value("${app.base-url:https://localhost:8443}")
    private String baseUrl;

    public AdminTelegramController(TelegramService telegramService) {
        this.telegramService = telegramService;
    }

    @PostMapping("/admin/telegram/register-webhook")
    public String registerWebhook(RedirectAttributes ra) {
        String webhookUrl = baseUrl + "/api/telegram/webhook";
        String result = telegramService.registerWebhook(webhookUrl);
        ra.addFlashAttribute("telegramResult", result);
        return "redirect:/admin";
    }
}
