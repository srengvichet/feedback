package kh.edu.num.feedback.web.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;

import jakarta.transaction.Transactional;
import kh.edu.num.feedback.domain.entity.AiFeedbackSummary;
import kh.edu.num.feedback.domain.repo.AiFeedbackSummaryRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class FeedbackAiService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackAiService.class);

    private final AnthropicClient client;
    private final AiFeedbackSummaryRepository aiSummaryRepo;

    public FeedbackAiService(AiFeedbackSummaryRepository aiSummaryRepo,
                              @Value("${anthropic.api-key:}") String configuredKey) {
        this.aiSummaryRepo = aiSummaryRepo;

        // Prefer application property; fall back to ANTHROPIC_API_KEY environment variable
        String apiKey = configuredKey.isBlank()
                ? System.getenv("ANTHROPIC_API_KEY")
                : configuredKey;

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("No Anthropic API key configured. " +
                     "Set ANTHROPIC_API_KEY env var or anthropic.api-key property. " +
                     "AI summaries will be unavailable.");
            apiKey = "";
        }

        this.client = AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .build();
    }

    // ── Public API ───────────────────────────────────────────────────────────

    public FeedbackAiSummary summarizeComments(
            Long sectionId,
            List<String> comments,
            String courseName,
            String teacherName) {

        if (comments == null || comments.isEmpty())
            return null;

        // 1. Check DB first — return cached if exists
        var existing = aiSummaryRepo.findBySectionId(sectionId);
        if (existing.isPresent()) {
            log.info("Loading AI summary from DB for section {}", sectionId);
            return toDto(existing.get());
        }

        // 2. Not in DB — generate from Claude
        log.info("Generating AI summary from Claude for section {}", sectionId);
        try {
            String prompt = buildPrompt(comments, courseName, teacherName);
            String raw = callClaude(prompt);
            FeedbackAiSummary dto = parseResponse(raw);

            // 3. Save to DB so we never call Claude again for this section
            saveToDb(sectionId, dto);

            return dto;

        } catch (Exception e) {
            log.error("Failed to generate AI summary: {}", e.getMessage());
            return null;
        }
    }

    @Transactional
    public FeedbackAiSummary regenerateSummary(
            Long sectionId,
            List<String> comments,
            String courseName,
            String teacherName) {

        log.info("Regenerating AI summary for section {}", sectionId);
        aiSummaryRepo.deleteBySectionId(sectionId);
        return summarizeComments(sectionId, comments, courseName, teacherName);
    }

    public FeedbackAiSummary translateToKhmer(Long sectionId, FeedbackAiSummary source) {
        if (source == null) return null;

        // Return cached Khmer translation if it exists
        var existing = aiSummaryRepo.findBySectionId(sectionId);
        if (existing.isPresent() && existing.get().getKhmerSummary() != null
                && !existing.get().getKhmerSummary().isBlank()) {
            log.info("Loading Khmer translation from DB for section {}", sectionId);
            var e = existing.get();
            return new FeedbackAiSummary(
                    e.getKhmerSummary(),
                    e.getKhmerStrengthsList(),
                    e.getKhmerImprovementsList(),
                    e.getKhmerRecommendation());
        }

        try {
            log.info("Translating AI summary to Khmer for section {}", sectionId);
            String prompt = buildTranslatePrompt(source);
            String raw = callClaude(prompt);
            FeedbackAiSummary khmer = parseResponse(raw);

            // Save Khmer translation back into the same DB row
            existing.ifPresent(entity -> {
                entity.setKhmerSummary(khmer.getSummary());
                entity.setKhmerStrengthsList(khmer.getStrengths());
                entity.setKhmerImprovementsList(khmer.getImprovements());
                entity.setKhmerRecommendation(khmer.getRecommendation());
                aiSummaryRepo.save(entity);
                log.info("Saved Khmer translation to DB for section {}", sectionId);
            });

            return khmer;
        } catch (Exception e) {
            log.error("Failed to translate AI summary to Khmer for section {}", sectionId, e);
            return null;
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private String callClaude(String prompt) {
        MessageCreateParams params = MessageCreateParams.builder()
                .model(Model.CLAUDE_OPUS_4_6)
                .maxTokens(4096L)
                .putAdditionalBodyProperty("thinking", com.anthropic.core.JsonValue.from(Map.of("type", "adaptive")))
                .addUserMessage(prompt)
                .build();

        Message response = client.messages().create(params);

        // Collect only text blocks (thinking blocks are automatically filtered out)
        return response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(textBlock -> textBlock.text())
                .collect(Collectors.joining());
    }

    private void saveToDb(Long sectionId, FeedbackAiSummary dto) {
        AiFeedbackSummary entity = new AiFeedbackSummary();
        entity.setSectionId(sectionId);
        entity.setSummary(dto.getSummary());
        entity.setStrengthsList(dto.getStrengths());
        entity.setImprovementsList(dto.getImprovements());
        entity.setRecommendation(dto.getRecommendation());
        aiSummaryRepo.save(entity);
        log.info("Saved AI summary to DB for section {}", sectionId);
    }

    private FeedbackAiSummary toDto(AiFeedbackSummary entity) {
        return new FeedbackAiSummary(
                entity.getSummary(),
                entity.getStrengthsList(),
                entity.getImprovementsList(),
                entity.getRecommendation());
    }

    private String buildPrompt(List<String> comments, String courseName, String teacherName) {
        String joined = comments.stream()
                .map(c -> "- " + c)
                .collect(Collectors.joining("\n"));

        String bulletInstruction = comments.size() < 3
                ? "STRENGTHS and IMPROVEMENTS: write only 1 bullet point each since feedback is limited."
                : "STRENGTHS and IMPROVEMENTS must each have exactly 2 bullet points maximum.";

        return """
                You are an educational assistant analyzing student feedback for a university.

                Context:
                - Course: %s
                - Teacher: %s
                - Total responses: %d

                IMPORTANT RULES:
                1. "Nothing" or "Nothing more" means the student has NO complaints — treat as positive/satisfied.
                2. Do NOT repeat student comment words as bullet points.
                3. Write your OWN analysis based on the meaning, not copy the comments.
                4. If comments are too vague or short, say so in the summary.
                5. %s
                6. Each bullet point must be a meaningful insight, NOT copied from comments.

                Reply ONLY in this exact format:

                SUMMARY:
                <2 sentences analyzing the overall sentiment and quality of feedback>

                STRENGTHS:
                - <your own insight about what the teacher does well>

                IMPROVEMENTS:
                - <your own suggestion to improve teaching or feedback quality>

                RECOMMENDATION:
                <1 short sentence the teacher can use as a reply to students>

                Student comments to analyze:
                %s
                """.formatted(courseName, teacherName, comments.size(), bulletInstruction, joined);
    }

    private String buildTranslatePrompt(FeedbackAiSummary source) {
        String strengths = (source.getStrengths() == null ? List.of() : source.getStrengths())
                .stream().map(s -> "- " + s).collect(Collectors.joining("\n"));
        String improvements = (source.getImprovements() == null ? List.of() : source.getImprovements())
                .stream().map(s -> "- " + s).collect(Collectors.joining("\n"));
        return """
                Translate the following educational feedback summary into Khmer (ភាសាខ្មែរ).
                Keep the exact same format with the same section labels in English (SUMMARY:, STRENGTHS:, IMPROVEMENTS:, RECOMMENDATION:).
                Only translate the content, not the section labels.
                Preserve bullet points with "- " prefix.

                SUMMARY:
                %s

                STRENGTHS:
                %s

                IMPROVEMENTS:
                %s

                RECOMMENDATION:
                %s
                """.formatted(
                source.getSummary(),
                strengths,
                improvements,
                source.getRecommendation());
    }

    private FeedbackAiSummary parseResponse(String raw) {
        String summary = extractSection(raw, "SUMMARY", "STRENGTHS");
        List<String> strengths = extractBullets(raw, "STRENGTHS", "IMPROVEMENTS");
        List<String> improvements = extractBullets(raw, "IMPROVEMENTS", "RECOMMENDATION");
        String recommendation = extractSection(raw, "RECOMMENDATION", null);
        return new FeedbackAiSummary(summary, strengths, improvements, recommendation);
    }

    private String extractSection(String text, String from, String to) {
        String upper = text.toUpperCase();
        int start = upper.indexOf(from + ":");
        if (start == -1)
            return "";
        start += (from + ":").length();
        int end = (to != null) ? upper.indexOf(to + ":", start) : text.length();
        if (end == -1)
            end = text.length();
        return text.substring(start, end).trim();
    }

    private List<String> extractBullets(String text, String from, String to) {
        String block = extractSection(text, from, to);
        List<String> bullets = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : block.lines().collect(Collectors.toList())) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            if (trimmed.matches("^[-•*].*")) {
                // New bullet point — save previous
                if (current.length() > 0) bullets.add(current.toString().trim());
                current = new StringBuilder(trimmed.replaceFirst("^[-•*]\\s*", "").trim());
            } else {
                // Continuation line — append with space
                if (current.length() > 0) current.append(" ").append(trimmed);
                else current.append(trimmed);
            }
        }
        if (current.length() > 0) bullets.add(current.toString().trim());
        return bullets;
    }
}
