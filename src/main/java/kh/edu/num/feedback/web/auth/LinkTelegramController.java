package kh.edu.num.feedback.web.auth;

import kh.edu.num.feedback.domain.entity.UserAccount;
import kh.edu.num.feedback.domain.repo.UserAccountRepository;
import kh.edu.num.feedback.security.UserPrincipal;
import kh.edu.num.feedback.service.TelegramService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class LinkTelegramController {

    private final TelegramService telegramService;
    private final UserAccountRepository userRepo;

    public LinkTelegramController(TelegramService telegramService,
                                   UserAccountRepository userRepo) {
        this.telegramService = telegramService;
        this.userRepo = userRepo;
    }

    @GetMapping("/link-telegram")
    public String linkPage(@AuthenticationPrincipal UserPrincipal principal, Model model) {
        UserAccount user = userRepo.findByUsername(principal.getUsername()).orElseThrow();
        model.addAttribute("linked", user.getTelegramChatId() != null);
        model.addAttribute("telegramEnabled", telegramService.isEnabled());
        model.addAttribute("botUsername", telegramService.getBotUsername());
        return "link-telegram";
    }

    @PostMapping("/link-telegram/generate")
    public String generateCode(@AuthenticationPrincipal UserPrincipal principal,
                                RedirectAttributes ra) {
        UserAccount user = userRepo.findByUsername(principal.getUsername()).orElseThrow();
        String code = telegramService.generateLinkCode(user.getId());
        ra.addFlashAttribute("linkCode", code);
        ra.addFlashAttribute("botUsername", telegramService.getBotUsername());
        return "redirect:/link-telegram";
    }

    @PostMapping("/link-telegram/unlink")
    public String unlink(@AuthenticationPrincipal UserPrincipal principal,
                          RedirectAttributes ra) {
        UserAccount user = userRepo.findByUsername(principal.getUsername()).orElseThrow();
        user.setTelegramChatId(null);
        userRepo.save(user);
        ra.addFlashAttribute("msg", "unlinked");
        return "redirect:/link-telegram";
    }
}
