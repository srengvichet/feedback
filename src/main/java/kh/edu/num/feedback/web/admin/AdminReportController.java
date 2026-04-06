package kh.edu.num.feedback.web.admin;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import kh.edu.num.feedback.domain.entity.EvaluationKind;
import kh.edu.num.feedback.domain.entity.UserAccount;
import kh.edu.num.feedback.domain.repo.AnswerRepository;
import kh.edu.num.feedback.domain.repo.ClassSectionRepository;
import kh.edu.num.feedback.domain.repo.EnrollmentRepository;
import kh.edu.num.feedback.domain.repo.SemesterRepository;
import kh.edu.num.feedback.domain.repo.StudentRegistryRepository;
import kh.edu.num.feedback.domain.repo.SubmissionRepository;
import kh.edu.num.feedback.web.admin.dto.AdminQuestionScoreStat;
import kh.edu.num.feedback.web.admin.dto.AdminSectionReportRow;
import kh.edu.num.feedback.web.admin.dto.AdminTeacherSummaryRow;
import kh.edu.num.feedback.domain.repo.AiFeedbackSummaryRepository;
import kh.edu.num.feedback.web.ai.FeedbackAiService;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.ResourceUtils;
import org.springframework.web.bind.annotation.*;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.UncheckedIOException;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import javax.imageio.ImageIO;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.pdf.JRPdfExporter;

@Controller
@RequestMapping("/admin/reports")
public class AdminReportController {

    private final SemesterRepository semesterRepo;
    private final ClassSectionRepository sectionRepo;
    private final SubmissionRepository submissionRepo;
    private final AnswerRepository answerRepo;
    private final EnrollmentRepository enrollmentRepo;
    private final SpringTemplateEngine templateEngine;
    private final StudentRegistryRepository studentRegistryRepo;
    private final FeedbackAiService feedbackAiService;
    private final AiFeedbackSummaryRepository aiSummaryRepo;

    public AdminReportController(SemesterRepository semesterRepo,
            ClassSectionRepository sectionRepo,
            SubmissionRepository submissionRepo,
            AnswerRepository answerRepo,
            EnrollmentRepository enrollmentRepo,
            SpringTemplateEngine templateEngine,
            StudentRegistryRepository studentRegistryRepo,
            FeedbackAiService feedbackAiService,
            AiFeedbackSummaryRepository aiSummaryRepo) {
        this.semesterRepo = semesterRepo;
        this.sectionRepo = sectionRepo;
        this.submissionRepo = submissionRepo;
        this.answerRepo = answerRepo;
        this.enrollmentRepo = enrollmentRepo;
        this.templateEngine = templateEngine;
        this.studentRegistryRepo = studentRegistryRepo;
        this.feedbackAiService = feedbackAiService;
        this.aiSummaryRepo = aiSummaryRepo;
    }

    // @GetMapping("/test-ai")
    // @ResponseBody
    // public Object testAi() {
    // return feedbackAiService.summarizeComments(List.of(
    // "The teacher explains clearly.",
    // "More practical examples are needed.",
    // "The teacher is friendly and helpful."));
    // }

    // ========= View Models (avoid Thymeleaf issues with record/projection access)
    // =========
    public static class SectionReportVm {
        private final Long sectionId;
        private final String teacherUsername;
        private final String courseCode;
        private final String courseName;
        private final String shiftTime;
        private final String building;
        private final String room;
        private final String sectionName;
        private final String semesterName;
        private final String submittedAtText;

        private final Long responses;
        private final Long enrolled;
        private final Double responseRate;

        private final Double avgCatA;
        private final Double avgCatB;
        private final Double avgCatC;
        private final Double avgCatD;
        private final Double avgCatE;
        private final Double overallAvg;
        private final String faculty;

        public SectionReportVm(Long sectionId,
                String teacherUsername,
                String courseCode,
                String courseName,
                String shiftTime,
                String building,
                String room,
                String sectionName,
                String semesterName,
                String submittedAtText,
                Long responses,
                Long enrolled,
                Double responseRate,
                Double avgCatA,
                Double avgCatB,
                Double avgCatC,
                Double avgCatD,
                Double avgCatE,
                Double overallAvg,
                String faculty) {
            this.sectionId = sectionId;
            this.teacherUsername = teacherUsername;
            this.courseCode = courseCode;
            this.courseName = courseName;
            this.shiftTime = shiftTime;
            this.building = building;
            this.room = room;
            this.sectionName = sectionName;
            this.semesterName = semesterName;
            this.submittedAtText = submittedAtText;
            this.responses = responses;
            this.enrolled = enrolled;
            this.responseRate = responseRate;
            this.avgCatA = avgCatA;
            this.avgCatB = avgCatB;
            this.avgCatC = avgCatC;
            this.avgCatD = avgCatD;
            this.avgCatE = avgCatE;
            this.overallAvg = overallAvg;
            this.faculty = faculty;
        }

        public Long getSectionId() {
            return sectionId;
        }

        public String getTeacherUsername() {
            return teacherUsername;
        }

        public String getCourseCode() {
            return courseCode;
        }

        public String getCourseName() {
            return courseName;
        }

        public String getShiftTime() {
            return shiftTime;
        }

        public String getBuilding() {
            return building;
        }

        public String getRoom() {
            return room;
        }

        public String getSectionName() {
            return sectionName;
        }

        public String getSemesterName() {
            return semesterName;
        }

        public String getSubmittedAtText() {
            return submittedAtText;
        }

        public Long getResponses() {
            return responses;
        }

        public Long getEnrolled() {
            return enrolled;
        }

        public Double getResponseRate() {
            return responseRate;
        }

        public Double getAvgCatA() {
            return avgCatA;
        }

        public Double getAvgCatB() {
            return avgCatB;
        }

        public Double getAvgCatC() {
            return avgCatC;
        }

        public Double getAvgCatD() {
            return avgCatD;
        }

        public Double getAvgCatE() {
            return avgCatE;
        }

        public Double getOverallAvg() {
            return overallAvg;
        }

        public String getFaculty() {
            return faculty;
        }
    }

    public static class TeacherSummaryVm {
        private final String teacherUsername;
        private final Long responses;
        private final Long enrolled;
        private final Double responseRate;
        private final Double avgCatA;
        private final Double avgCatB;
        private final Double avgCatC;
        private final Double avgCatD;
        private final Double avgCatE;
        private final Double overallAvg;

        public TeacherSummaryVm(String teacherUsername, Long responses, Long enrolled, Double responseRate,
                Double avgCatA, Double avgCatB, Double avgCatC, Double avgCatD, Double avgCatE, Double overallAvg) {
            this.teacherUsername = teacherUsername;
            this.responses = responses;
            this.enrolled = enrolled;
            this.responseRate = responseRate;
            this.avgCatA = avgCatA;
            this.avgCatB = avgCatB;
            this.avgCatC = avgCatC;
            this.avgCatD = avgCatD;
            this.avgCatE = avgCatE;
            this.overallAvg = overallAvg;
        }

        public static TeacherSummaryVm from(AdminTeacherSummaryRow r) {
            return new TeacherSummaryVm(
                    r.teacherUsername(),
                    r.responses(),
                    r.enrolled(),
                    r.responseRate(),
                    r.avgCatA(),
                    r.avgCatB(),
                    r.avgCatC(),
                    r.avgCatD(),
                    r.avgCatE(),
                    r.overallAvg());
        }

        public String getTeacherUsername() {
            return teacherUsername;
        }

        public Long getResponses() {
            return responses;
        }

        public Long getEnrolled() {
            return enrolled;
        }

        public Double getResponseRate() {
            return responseRate;
        }

        public Double getAvgCatA() {
            return avgCatA;
        }

        public Double getAvgCatB() {
            return avgCatB;
        }

        public Double getAvgCatC() {
            return avgCatC;
        }

        public Double getAvgCatD() {
            return avgCatD;
        }

        public Double getAvgCatE() {
            return avgCatE;
        }

        public Double getOverallAvg() {
            return overallAvg;
        }
    }

    public static class QuestionStatVm {
        private final Integer orderNo;
        private final String questionText;
        private final Integer minScore;
        private final Integer maxScore;
        private final Double avgScore;
        private final Long n;

        public QuestionStatVm(Integer orderNo, String questionText, Integer minScore, Integer maxScore, Double avgScore,
                Long n) {
            this.orderNo = orderNo;
            this.questionText = questionText;
            this.minScore = minScore;
            this.maxScore = maxScore;
            this.avgScore = avgScore;
            this.n = n;
        }

        public static QuestionStatVm from(AdminQuestionScoreStat s) {
            return new QuestionStatVm(s.orderNo(), s.questionText(), s.minScore(), s.maxScore(), s.avgScore(), s.n());
        }

        public Integer getOrderNo() {
            return orderNo;
        }

        public String getQuestionText() {
            return questionText;
        }

        public Integer getMinScore() {
            return minScore;
        }

        public Integer getMaxScore() {
            return maxScore;
        }

        public Double getAvgScore() {
            return avgScore;
        }

        public Long getN() {
            return n;
        }
    }

    // ========= Pages =========
    @GetMapping
    public String page(@RequestParam(required = false) Long semesterId,
            @RequestParam(required = false) EvaluationKind kind,
            Model model) {

        var semesters = semesterRepo.findAll();
        var resolvedKind = (kind == null) ? EvaluationKind.STUDENT_FEEDBACK : kind;

        Long resolvedSemesterId = semesterId;
        if (resolvedSemesterId == null && !semesters.isEmpty()) {
            resolvedSemesterId = semesters.get(0).getId();
        }
        final Long semId = resolvedSemesterId;

        var selectedSemester = (semId == null) ? null
                : semesters.stream()
                        .filter(s -> Objects.equals(s.getId(), semId))
                        .findFirst()
                        .orElse(null);

        model.addAttribute("semesters", semesters);
        model.addAttribute("semesterId", semId);
        model.addAttribute("selectedSemester", selectedSemester);
        model.addAttribute("kind", resolvedKind);
        model.addAttribute("isTeacherSelf", resolvedKind == EvaluationKind.TEACHER_SELF);
        populateCategoryMetadata(model, resolvedKind);

        if (resolvedKind == EvaluationKind.TEACHER_SELF) {
            populateTeacherSelfPage(semId, model);
        } else {
            populateStudentFeedbackPage(semId, model);
        }

        return "admin/reports";
    }

    private void populateStudentFeedbackPage(Long semId, Model model) {
        var kind = EvaluationKind.STUDENT_FEEDBACK;
        List<AdminSectionReportRow> rawRows = (semId == null)
                ? Collections.emptyList()
                : answerRepo.adminSectionReport(semId, kind);

        // enrolledMap: sectionId -> enrolled count
        Map<Long, Long> enrolledMap = new HashMap<>();
        if (semId != null) {
            for (var it : enrollmentRepo.countEnrollmentsBySemesterGroupBySection(semId)) {
                enrolledMap.put(it.getSectionId(), it.getEnrolled());
            }
        }

        // Build sectionId -> faculty map
        Map<Long, String> sectionFacultyMap = new HashMap<>();
        if (semId != null) {
            for (var sec : sectionRepo.findBySemesterId(semId)) {
                String fac = (sec.getCohort() != null && sec.getCohort().getFaculty() != null)
                        ? sec.getCohort().getFaculty() : "";
                sectionFacultyMap.put(sec.getId(), fac);
            }
        }

        // Distinct faculty list for filter dropdown
        List<String> faculties = sectionFacultyMap.values().stream()
                .filter(f -> f != null && !f.isBlank())
                .distinct().sorted().toList();

        // Build VM rows
        List<SectionReportVm> rows = new ArrayList<>();
        for (var r : rawRows) {
            long enrolled = enrolledMap.getOrDefault(r.sectionId(), 0L);
            long resp = (r.responses() == null) ? 0L : r.responses();
            Double rate = (enrolled == 0) ? null : (resp * 100.0 / enrolled);

            rows.add(new SectionReportVm(
                    r.sectionId(),
                    r.teacherUsername(),
                    r.courseCode(),
                    r.courseName(),
                    String.valueOf(r.shiftTime()),
                    r.building(),
                    r.room(),
                    r.sectionName(),
                    r.semesterName(),
                    formatSubmittedAt(r.submittedAt()),
                    resp,
                    enrolled,
                    rate,
                    r.avgCatA(),
                    r.avgCatB(),
                    r.avgCatC(),
                    r.avgCatD(),
                    r.avgCatE(),
                    r.overallAvg(),
                    sectionFacultyMap.getOrDefault(r.sectionId(), "")));
        }

        long totalResponses = rows.stream().mapToLong(x -> x.getResponses() == null ? 0L : x.getResponses()).sum();
        long totalEnrolled = rows.stream().mapToLong(x -> x.getEnrolled() == null ? 0L : x.getEnrolled()).sum();

        Double overallResponseRate = (totalEnrolled == 0) ? null : (totalResponses * 100.0 / totalEnrolled);

        // Teacher rankings
        List<AdminTeacherSummaryRow> teacherSummariesRaw = buildTeacherSummaries(rawRows, enrolledMap);
        List<TeacherSummaryVm> teacherSummaries = teacherSummariesRaw.stream()
                .map(TeacherSummaryVm::from)
                .toList();

        model.addAttribute("topOverall", topBy(teacherSummaries, TeacherSummaryVm::getOverallAvg, 5));
        model.addAttribute("bottomOverall", bottomBy(teacherSummaries, TeacherSummaryVm::getOverallAvg, 5));

        model.addAttribute("topA", topBy(teacherSummaries, TeacherSummaryVm::getAvgCatA, 5));
        model.addAttribute("bottomA", bottomBy(teacherSummaries, TeacherSummaryVm::getAvgCatA, 5));

        model.addAttribute("topB", topBy(teacherSummaries, TeacherSummaryVm::getAvgCatB, 5));
        model.addAttribute("bottomB", bottomBy(teacherSummaries, TeacherSummaryVm::getAvgCatB, 5));

        model.addAttribute("topC", topBy(teacherSummaries, TeacherSummaryVm::getAvgCatC, 5));
        model.addAttribute("bottomC", bottomBy(teacherSummaries, TeacherSummaryVm::getAvgCatC, 5));

        model.addAttribute("topD", topBy(teacherSummaries, TeacherSummaryVm::getAvgCatD, 5));
        model.addAttribute("bottomD", bottomBy(teacherSummaries, TeacherSummaryVm::getAvgCatD, 5));

        model.addAttribute("topE", topBy(teacherSummaries, TeacherSummaryVm::getAvgCatE, 5));
        model.addAttribute("bottomE", bottomBy(teacherSummaries, TeacherSummaryVm::getAvgCatE, 5));

        model.addAttribute("rows", rows);
        model.addAttribute("faculties", faculties);
        model.addAttribute("kind", kind);

        model.addAttribute("totalResponses", totalResponses);
        model.addAttribute("totalEnrolled", totalEnrolled);
        model.addAttribute("overallResponseRate", overallResponseRate);
        model.addAttribute("totalSections", rows.size());
    }

    @GetMapping("/{sectionId}")
    public String detail(@PathVariable Long sectionId,
            @RequestParam(required = false) Long semesterId,
            @RequestParam(required = false) EvaluationKind kind,
            Model model) {
        var resolvedKind = (kind == null) ? EvaluationKind.STUDENT_FEEDBACK : kind;
        if (resolvedKind == EvaluationKind.TEACHER_SELF) {
            return teacherSelfDetail(sectionId, semesterId, model);
        }

        var studentKind = EvaluationKind.STUDENT_FEEDBACK;

        var section = sectionRepo.findById(sectionId).orElseThrow();
        var qStatsRaw = answerRepo.adminQuestionStats(sectionId, studentKind);
        var comments = answerRepo.adminComments(sectionId, studentKind);
        var count = submissionRepo.countByKindAndSectionId(studentKind, sectionId);

        long enrolled = enrollmentRepo.countBySection_Id(sectionId);
        Double responseRate = (enrolled == 0) ? null : (count * 100.0 / enrolled);

        Double catA = avgRange(qStatsRaw, 1, 10);
        Double catB = avgRange(qStatsRaw, 11, 13);
        Double catC = avgRange(qStatsRaw, 14, 18);
        Double catD = avgRange(qStatsRaw, 19, 21);
        Double catE = avgRange(qStatsRaw, 22, 26);
        Double overall = avgRange(qStatsRaw, 1, 999);

        List<QuestionStatVm> qStats = qStatsRaw.stream().map(QuestionStatVm::from).toList();

        model.addAttribute("section", section);
        model.addAttribute("qStats", qStats);
        model.addAttribute("comments", comments);
        model.addAttribute("count", count);
        model.addAttribute("enrolled", enrolled);
        model.addAttribute("responseRate", responseRate);
        model.addAttribute("catA", catA);
        model.addAttribute("catB", catB);
        model.addAttribute("catC", catC);
        model.addAttribute("catD", catD);
        model.addAttribute("catE", catE);
        model.addAttribute("overall", overall);
        model.addAttribute("kind", studentKind);
        model.addAttribute("semesterId", section.getSemester() != null ? section.getSemester().getId() : semesterId);

        return "admin/report_detail";
    }

    private void populateTeacherSelfPage(Long semId, Model model) {
        Map<Long, String> eligibleTeachers = findTeacherDisplayNamesBySemester(semId);
        String semesterName = (semId == null)
            ? ""
            : semesterRepo.findById(semId).map(s -> s.getName()).orElse("");

        List<AdminSectionReportRow> submittedRows = (semId == null)
                ? Collections.emptyList()
                : answerRepo.adminTeacherSelfReport(semId, EvaluationKind.TEACHER_SELF);

        Map<Long, AdminSectionReportRow> submittedByTeacherId = submittedRows.stream()
                .collect(Collectors.toMap(
                        AdminSectionReportRow::sectionId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new));

        for (var row : submittedRows) {
            eligibleTeachers.putIfAbsent(row.sectionId(), row.teacherUsername());
        }

        Map<Long, Long> eligibleMap = new HashMap<>();
        List<AdminSectionReportRow> mergedRows = new ArrayList<>();
        for (var entry : eligibleTeachers.entrySet()) {
            Long teacherId = entry.getKey();
            eligibleMap.put(teacherId, 1L);

            var existing = submittedByTeacherId.get(teacherId);
            if (existing != null) {
                mergedRows.add(existing);
                continue;
            }

            mergedRows.add(new AdminSectionReportRow(
                    teacherId,
                    semesterName,
                    "",
                    "Teacher Self-Assessment",
                    entry.getValue(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    0L,
                    null));
        }

        List<SectionReportVm> rows = mergedRows.stream()
                .map(r -> {
                    long resp = (r.responses() == null) ? 0L : r.responses();
                    long eligible = eligibleMap.getOrDefault(r.sectionId(), 1L);
                    Double rate = (eligible == 0) ? null : (resp * 100.0 / eligible);
                    return new SectionReportVm(
                            r.sectionId(),
                            r.teacherUsername(),
                            r.courseCode(),
                            r.courseName(),
                            null,
                            null,
                            null,
                            null,
                            r.semesterName(),
                            formatSubmittedAt(r.submittedAt()),
                            resp,
                            eligible,
                            rate,
                            r.avgCatA(),
                            r.avgCatB(),
                            r.avgCatC(),
                            r.avgCatD(),
                            r.avgCatE(),
                            r.overallAvg(),
                            "");
                })
                .sorted(Comparator.comparing(SectionReportVm::getTeacherUsername, String.CASE_INSENSITIVE_ORDER))
                .toList();

        long totalResponses = rows.stream().mapToLong(x -> x.getResponses() == null ? 0L : x.getResponses()).sum();
        long totalEligible = eligibleMap.size();
        Double overallResponseRate = (totalEligible == 0) ? null : (totalResponses * 100.0 / totalEligible);

        List<AdminTeacherSummaryRow> teacherSummariesRaw = buildTeacherSummaries(mergedRows, eligibleMap);
        List<TeacherSummaryVm> teacherSummaries = teacherSummariesRaw.stream()
                .map(TeacherSummaryVm::from)
                .toList();

        model.addAttribute("topOverall", topBy(teacherSummaries, TeacherSummaryVm::getOverallAvg, 5));
        model.addAttribute("bottomOverall", bottomBy(teacherSummaries, TeacherSummaryVm::getOverallAvg, 5));
        model.addAttribute("topA", topBy(teacherSummaries, TeacherSummaryVm::getAvgCatA, 5));
        model.addAttribute("bottomA", bottomBy(teacherSummaries, TeacherSummaryVm::getAvgCatA, 5));
        model.addAttribute("topB", topBy(teacherSummaries, TeacherSummaryVm::getAvgCatB, 5));
        model.addAttribute("bottomB", bottomBy(teacherSummaries, TeacherSummaryVm::getAvgCatB, 5));
        model.addAttribute("topC", topBy(teacherSummaries, TeacherSummaryVm::getAvgCatC, 5));
        model.addAttribute("bottomC", bottomBy(teacherSummaries, TeacherSummaryVm::getAvgCatC, 5));
        model.addAttribute("topD", topBy(teacherSummaries, TeacherSummaryVm::getAvgCatD, 5));
        model.addAttribute("bottomD", bottomBy(teacherSummaries, TeacherSummaryVm::getAvgCatD, 5));
        model.addAttribute("topE", Collections.emptyList());
        model.addAttribute("bottomE", Collections.emptyList());

        model.addAttribute("rows", rows);
        model.addAttribute("faculties", Collections.emptyList());
        model.addAttribute("totalResponses", totalResponses);
        model.addAttribute("totalEnrolled", totalEligible);
        model.addAttribute("overallResponseRate", overallResponseRate);
        model.addAttribute("totalSections", rows.size());
    }

    private String teacherSelfDetail(Long teacherId, Long semesterId, Model model) {
        if (semesterId == null) {
            throw new IllegalArgumentException("semesterId is required for teacher self-assessment reports.");
        }

        var kind = EvaluationKind.TEACHER_SELF;
        var semester = semesterRepo.findById(semesterId).orElseThrow();
        var qStatsRaw = answerRepo.adminTeacherSelfQuestionStats(semesterId, teacherId, kind);
        var comments = answerRepo.adminTeacherSelfComments(semesterId, teacherId, kind);
        var count = submissionRepo.countByKindAndSemester_IdAndSectionIsNullAndSubmittedBy_Id(kind, semesterId, teacherId);
        var submission = submissionRepo.findByKindAndSemester_IdAndSectionIsNullAndSubmittedBy_Id(kind, semesterId, teacherId).orElse(null);

        Map<Long, String> teacherNames = findTeacherDisplayNamesBySemester(semesterId);
        String teacherName = teacherNames.getOrDefault(teacherId,
                answerRepo.adminTeacherSelfReport(semesterId, kind).stream()
                        .filter(r -> Objects.equals(r.sectionId(), teacherId))
                        .map(AdminSectionReportRow::teacherUsername)
                        .findFirst()
                        .orElse("Teacher #" + teacherId));

        long eligible = 1L;
        Double responseRate = (count == 0) ? 0.0 : 100.0;

        Double catA = avgRange(qStatsRaw, 1, 5);
        Double catB = avgRange(qStatsRaw, 6, 10);
        Double catC = avgRange(qStatsRaw, 11, 15);
        Double catD = avgRange(qStatsRaw, 16, 20);
        Double overall = avgRange(qStatsRaw, 1, 999);

        List<QuestionStatVm> qStats = qStatsRaw.stream().map(QuestionStatVm::from).toList();

        model.addAttribute("teacherName", teacherName);
        model.addAttribute("teacherId", teacherId);
        model.addAttribute("submittedAtText", submission == null ? null : formatSubmittedAt(submission.getSubmittedAt()));
        model.addAttribute("semester", semester);
        model.addAttribute("qStats", qStats);
        model.addAttribute("comments", comments);
        model.addAttribute("count", count);
        model.addAttribute("enrolled", eligible);
        model.addAttribute("responseRate", responseRate);
        model.addAttribute("catA", catA);
        model.addAttribute("catB", catB);
        model.addAttribute("catC", catC);
        model.addAttribute("catD", catD);
        model.addAttribute("catE", null);
        model.addAttribute("overall", overall);
        model.addAttribute("kind", kind);
        model.addAttribute("semesterId", semesterId);

        return "admin/report_detail_teacher_self";
    }

    // Update getAiSummary to pass sectionId
    @GetMapping("/{sectionId}/ai-summary")
    @ResponseBody
    public ResponseEntity<?> getAiSummary(@PathVariable Long sectionId) {
        try {
            var kind = EvaluationKind.STUDENT_FEEDBACK;
            var section = sectionRepo.findById(sectionId).orElseThrow();
            var comments = answerRepo.adminComments(sectionId, kind);

            String courseName = (section.getCourse() != null)
                    ? section.getCourse().getCode() + " - " + section.getCourse().getName()
                    : "Unknown Course";
            String teacherName = (section.getTeacher() != null)
                    ? section.getTeacher().getUsername()
                    : "Unknown Teacher";

            // ✅ Pass sectionId so service checks DB first
            var summary = feedbackAiService.summarizeComments(
                    sectionId, comments, courseName, teacherName);

            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            return ResponseEntity.ok(null);
        }
    }

    // ✅ New endpoint for Regenerate button
    @PostMapping("/{sectionId}/ai-summary/regenerate")
    @ResponseBody
    public ResponseEntity<?> regenerateAiSummary(@PathVariable Long sectionId) {
        try {
            var kind = EvaluationKind.STUDENT_FEEDBACK;
            var section = sectionRepo.findById(sectionId).orElseThrow();
            var comments = answerRepo.adminComments(sectionId, kind);

            String courseName = (section.getCourse() != null)
                    ? section.getCourse().getCode() + " - " + section.getCourse().getName()
                    : "Unknown Course";
            String teacherName = (section.getTeacher() != null)
                    ? section.getTeacher().getUsername()
                    : "Unknown Teacher";

            var summary = feedbackAiService.regenerateSummary(
                    sectionId, comments, courseName, teacherName);

            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            return ResponseEntity.ok(null);
        }
    }

    @PostMapping("/{sectionId}/ai-summary/translate")
    @ResponseBody
    public ResponseEntity<?> translateAiSummary(@PathVariable Long sectionId) {
        try {
            var kind = EvaluationKind.STUDENT_FEEDBACK;
            var section = sectionRepo.findById(sectionId).orElseThrow();
            var comments = answerRepo.adminComments(sectionId, kind);

            String courseName = (section.getCourse() != null)
                    ? section.getCourse().getCode() + " - " + section.getCourse().getName()
                    : "Unknown Course";
            String teacherName = (section.getTeacher() != null)
                    ? section.getTeacher().getUsername()
                    : "Unknown Teacher";

            var englishSummary = feedbackAiService.summarizeComments(sectionId, comments, courseName, teacherName);
            if (englishSummary == null)
                return ResponseEntity.badRequest().body(Map.of("error", "No English summary available to translate."));

            var khmerSummary = feedbackAiService.translateToKhmer(sectionId, englishSummary);
            if (khmerSummary == null)
                return ResponseEntity.badRequest().body(Map.of("error", "Translation failed. Check ANTHROPIC_API_KEY in launch.json."));

            return ResponseEntity.ok(khmerSummary);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // ========= Export CSV (semester) =========
    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<byte[]> exportCsv(@RequestParam Long semesterId,
            @RequestParam(required = false) EvaluationKind kind) {
        var resolvedKind = (kind == null) ? EvaluationKind.STUDENT_FEEDBACK : kind;

        if (resolvedKind == EvaluationKind.TEACHER_SELF) {
            return exportTeacherSelfCsv(semesterId);
        }

        List<AdminSectionReportRow> rows = answerRepo.adminSectionReport(semesterId, resolvedKind);

        Map<Long, Long> enrolledMap = enrollmentRepo.countEnrollmentsBySemesterGroupBySection(semesterId)
                .stream()
                .collect(Collectors.toMap(
                        EnrollmentRepository.SectionEnrollCount::getSectionId,
                        EnrollmentRepository.SectionEnrollCount::getEnrolled));

        StringBuilder sb = new StringBuilder();
        sb.append("Teacher,CourseCode,CourseName,Shift,Building,Room,Section,Responses,Enrolled,ResponseRate%,")
                .append("CatA(Q1-10),CatB(Q11-13),CatC(Q14-18),CatD(Q19-21),CatE(Q22-26),Overall\n");

        for (var r : rows) {
            long enrolled = enrolledMap.getOrDefault(r.sectionId(), 0L);
            long resp = r.responses() == null ? 0L : r.responses();
            Double rate = (enrolled == 0) ? null : (resp * 100.0 / enrolled);

            sb.append(csv(r.teacherUsername())).append(',')
                    .append(csv(r.courseCode())).append(',')
                    .append(csv(r.courseName())).append(',')
                    .append(csv(String.valueOf(r.shiftTime()))).append(',')
                    .append(csv(r.building())).append(',')
                    .append(csv(r.room())).append(',')
                    .append(csv(r.sectionName())).append(',')
                    .append(resp).append(',')
                    .append(enrolled).append(',')
                    .append(rate == null ? "" : String.format(Locale.US, "%.2f", rate)).append(',')
                    .append(num(r.avgCatA())).append(',')
                    .append(num(r.avgCatB())).append(',')
                    .append(num(r.avgCatC())).append(',')
                    .append(num(r.avgCatD())).append(',')
                    .append(num(r.avgCatE())).append(',')
                    .append(num(r.overallAvg()))
                    .append('\n');
        }

        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=admin_reports_semester_" + semesterId + ".csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(bytes);
    }

        private ResponseEntity<byte[]> exportTeacherSelfCsv(Long semesterId) {
        Map<Long, String> eligibleTeachers = findTeacherDisplayNamesBySemester(semesterId);
        List<AdminSectionReportRow> submittedRows = answerRepo.adminTeacherSelfReport(semesterId, EvaluationKind.TEACHER_SELF);
        Map<Long, AdminSectionReportRow> submittedByTeacherId = submittedRows.stream()
            .collect(Collectors.toMap(
                AdminSectionReportRow::sectionId,
                Function.identity(),
                (left, right) -> left,
                LinkedHashMap::new));

        for (var row : submittedRows) {
            eligibleTeachers.putIfAbsent(row.sectionId(), row.teacherUsername());
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Teacher,Submitted,Eligible,CompletionRate%,CatA(Q1-5),CatB(Q6-10),CatC(Q11-15),CatD(Q16-20),Overall\n");

        for (var entry : eligibleTeachers.entrySet()) {
            Long teacherId = entry.getKey();
            var row = submittedByTeacherId.get(teacherId);
            long submitted = (row == null || row.responses() == null) ? 0L : row.responses();
            double rate = submitted > 0 ? 100.0 : 0.0;

            sb.append(csv(entry.getValue())).append(',')
                .append(submitted).append(',')
                .append(1).append(',')
                .append(String.format(Locale.US, "%.2f", rate)).append(',')
                .append(num(row == null ? null : row.avgCatA())).append(',')
                .append(num(row == null ? null : row.avgCatB())).append(',')
                .append(num(row == null ? null : row.avgCatC())).append(',')
                .append(num(row == null ? null : row.avgCatD())).append(',')
                .append(num(row == null ? null : row.overallAvg()))
                .append('\n');
        }

        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=admin_teacher_self_reports_semester_" + semesterId + ".csv")
            .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
            .body(bytes);
        }

    // ========= Export CSV (section detail) =========
    @GetMapping(value = "/{sectionId}/export", produces = "text/csv")
    public ResponseEntity<byte[]> exportSectionCsv(@PathVariable Long sectionId) {
        var kind = EvaluationKind.STUDENT_FEEDBACK;

        var section = sectionRepo.findById(sectionId).orElseThrow();
        var qStats = answerRepo.adminQuestionStats(sectionId, kind);
        var comments = answerRepo.adminComments(sectionId, kind);

        StringBuilder sb = new StringBuilder();

        // ✅ UTF-8 BOM for Excel (fix Khmer "????")
        sb.append('\uFEFF');

        sb.append("Course,").append(csv(section.getCourse().getCode() + " - " + section.getCourse().getName()))
                .append("\n");
        sb.append("Teacher,").append(csv(section.getTeacher().getUsername())).append("\n\n");

        sb.append("No,Question,Min,Max,Avg,N\n");
        for (var q : qStats) {
            sb.append(q.orderNo()).append(',')
                    .append(csv(q.questionText())).append(',')
                    .append(q.minScore() == null ? "" : q.minScore()).append(',')
                    .append(q.maxScore() == null ? "" : q.maxScore()).append(',')
                    .append(q.avgScore() == null ? "" : String.format(Locale.US, "%.2f", q.avgScore())).append(',')
                    .append(q.n() == null ? "" : q.n())
                    .append('\n');
        }

        sb.append("\nComments\n");
        for (var c : comments) {
            sb.append(csv(c)).append('\n');
        }

        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=section_report_" + sectionId + ".csv")
                // ✅ ensure charset in header (helps non-Excel clients too)
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(bytes);
    }

    // ========= Export PDF (section detail) =========
    public static class PdfHeaderVm {
        private final String teacherName;
        private final String teachingClass;
        private final String cohort;
        private final String promotion;
        private final String shiftTime;
        private final String semesterName;
        private final String course;
        private final String room;

        public PdfHeaderVm(String teacherName, String teachingClass, String cohort, String promotion,
                String shiftTime, String semesterName, String course, String room) {
            this.teacherName = teacherName;
            this.teachingClass = teachingClass;
            this.cohort = cohort;
            this.promotion = promotion;
            this.shiftTime = shiftTime;
            this.semesterName = semesterName;
            this.course = course;
            this.room = room;
        }

        public String getTeacherName() {
            return teacherName;
        }

        public String getTeachingClass() {
            return teachingClass;
        }

        public String getCohort() {
            return cohort;
        }

        public String getPromotion() {
            return promotion;
        }

        public String getShiftTime() {
            return shiftTime;
        }

        public String getSemesterName() {
            return semesterName;
        }

        public String getCourse() {
            return course;
        }

        public String getRoom() {
            return room;
        }
    }

    public static class PdfQuestionVm {
        private final Integer orderNo;
        private final String questionText;
        private final Integer minScore;
        private final Integer maxScore;
        private final Double avgScore;
        private final Long n;

        public PdfQuestionVm(Integer orderNo, String questionText, Integer minScore, Integer maxScore, Double avgScore,
                Long n) {
            this.orderNo = orderNo;
            this.questionText = questionText;
            this.minScore = minScore;
            this.maxScore = maxScore;
            this.avgScore = avgScore;
            this.n = n;
        }

        public static PdfQuestionVm from(AdminQuestionScoreStat s) {
            return new PdfQuestionVm(s.orderNo(), s.questionText(), s.minScore(), s.maxScore(), s.avgScore(), s.n());
        }

        public Integer getOrderNo() {
            return orderNo;
        }

        public String getQuestionText() {
            return questionText;
        }

        public Integer getMinScore() {
            return minScore;
        }

        public Integer getMaxScore() {
            return maxScore;
        }

        public Double getAvgScore() {
            return avgScore;
        }

        public Long getN() {
            return n;
        }
    }

    public static class PdfCategoryVm {
        private final String label; // ក, ខ, គ, ឃ, ង
        private final int start;
        private final int end;
        private final String rangeText; // (Q1–Q10)
        private final String avgText;
        private final List<PdfQuestionVm> questions;

        public PdfCategoryVm(String label, int start, int end, String rangeText, String avgText,
                List<PdfQuestionVm> questions) {
            this.label = label;
            this.start = start;
            this.end = end;
            this.rangeText = rangeText;
            this.avgText = avgText;
            this.questions = questions;
        }

        public String getLabel() {
            return label;
        }

        public int getStart() {
            return start;
        }

        public int getEnd() {
            return end;
        }

        public String getRangeText() {
            return rangeText;
        }

        public String getAvgText() {
            return avgText;
        }

        public List<PdfQuestionVm> getQuestions() {
            return questions;
        }
    }

    @GetMapping(value = "/{sectionId}/export.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportSectionPdf(@PathVariable Long sectionId,
            @RequestParam(defaultValue = "en") String lang) {

        var kind = EvaluationKind.STUDENT_FEEDBACK;

        var section = sectionRepo.findById(sectionId).orElseThrow();
        var qStats = answerRepo.adminQuestionStats(sectionId, kind);
        var comments = answerRepo.adminComments(sectionId, kind)
                .stream()
                .filter(c -> c != null && !c.isBlank())
                .map(String::trim)
                .distinct()
                .limit(20)
                .collect(Collectors.joining("\n• ", "• ", ""));
        List<String> commentList = answerRepo.adminComments(sectionId, kind)
                .stream()
                .filter(c -> c != null && !c.isBlank())
                .map(String::trim)
                .distinct()
                .limit(30)
                .toList();

        long count = submissionRepo.countByKindAndSectionId(kind, sectionId);
        long enrolled = enrollmentRepo.countBySection_Id(sectionId);
        Double responseRate = (enrolled == 0) ? null : (count * 100.0 / enrolled);
        Double overall = avgRange(qStats, 1, 999);
        long responded = count;
        long notResponded = Math.max(0, enrolled - responded);

        String teacherName = firstNonBlank(
                safeStr(() -> reflectString(section.getTeacher(), "getFullName", "getName")),
                safeStr(() -> section.getTeacher().getUsername()),
                "-");

        String teachingClass = firstNonBlank(
                safeStr(() -> reflectString(section, "getTeachingClass", "getClassName", "getClassCode")),
                safeStr(() -> section.getSectionName()),
                "-");

        String cohort = firstNonBlank(
                safeStr(() -> section.getCohort() != null ? String.valueOf(section.getCohort().getCohortNo()) : null),
                "-");

        String promotion = firstNonBlank(
                safeStr(() -> section.getCohort() != null ? String.valueOf(section.getCohort().getCohortNo()) : null),
                "-");

        String shiftTime = firstNonBlank(safeStr(() -> String.valueOf(section.getShiftTime())), "-");

        String semesterName = firstNonBlank(
                safeStr(() -> section.getSemester() != null ? section.getSemester().getName() : null),
                "-");

        String course = firstNonBlank(
                safeStr(() -> section.getCourse() != null
                        ? section.getCourse().getCode() + " - " + section.getCourse().getName()
                        : null),
                "-");

        String room = firstNonBlank(
                safeStr(() -> (section.getBuilding() != null ? section.getBuilding() : "-") + "-"
                        + (section.getRoom() != null ? section.getRoom() : "-")),
                "-");

        // categories (same idea as your existing code)
        List<PdfCategoryVm> categories = List.of(
                buildCatVm(addKhmerBreaks("ក.ចំណេះដឹង និងជំនាញរបស់គ្រូបង្រៀន", 100), 1, 10, qStats),
                buildCatVm(addKhmerBreaks("ខ.ការផ្តល់ឯកសារសិក្សាដល់និស្សិត", 100), 11, 13, qStats),
                buildCatVm(addKhmerBreaks("គ.ការអនុវត្តតាមគោលការណ៍របស់សាកលវិទ្យាល័យ", 100), 14, 18, qStats),
                buildCatVm(addKhmerBreaks("ឃ.ទំនាក់ទំនងរវាងគ្រូបង្រៀនជាមួយនិស្សិត", 100), 19, 21, qStats),
                buildCatVm(addKhmerBreaks("ង.ក្រមសីលធម៌វិជ្ជាជីវៈ", 100), 22, 26, qStats));

        // flatten rows for Jasper
        List<JasperRowVm> rows = new ArrayList<>();

        for (PdfCategoryVm cat : categories) {
            if (cat.getQuestions() == null || cat.getQuestions().isEmpty()) {
                rows.add(new JasperRowVm(
                        cat.getLabel(), cat.getRangeText(), cat.getAvgText(),
                        null, "No questions found in this category.", "-", "-", "-", "-"));
            } else {
                for (PdfQuestionVm q : cat.getQuestions()) {
                    String nText;
                    if (q.getN() == null) {
                        nText = "-";
                    } else if (enrolled <= 0) {
                        nText = String.valueOf(q.getN());
                    } else {
                        double pct = (q.getN() * 100.0) / enrolled;
                        nText = String.format(Locale.US, "%d (%.0f%%)", q.getN(), pct); // short, fits width=50
                    }
                    rows.add(new JasperRowVm(
                            cat.getLabel(), cat.getRangeText(), cat.getAvgText(),
                            q.getOrderNo(),
                            addKhmerBreaks(q.getQuestionText(), 45),
                            q.getMinScore() == null ? "-" : String.valueOf(q.getMinScore()),
                            q.getMaxScore() == null ? "-" : String.valueOf(q.getMaxScore()),
                            q.getAvgScore() == null ? "-" : String.format(Locale.US, "%.2f", q.getAvgScore()),
                            nText));
                }
            }
        }

        String generatedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

        Map<String, Object> params = new HashMap<>();
        params.put(JRParameter.REPORT_LOCALE, "km".equalsIgnoreCase(lang) ? Locale.forLanguageTag("km") : Locale.ENGLISH);

        params.put("teacherName", teacherName);
        params.put("teachingClass", teachingClass);
        params.put("cohort", cohort);
        params.put("promotion", promotion);
        params.put("shiftTime", shiftTime);
        params.put("semesterName", semesterName);
        params.put("course", course);
        params.put("room", room);
        params.put("comments", comments);
        params.put("aiStrengthsLines", List.of());
        params.put("aiImprovementsLines", List.of());

        // AI summary — use language chosen by admin (lang=km for Khmer, lang=en for English)
        String courseName = (section.getCourse() != null)
                ? section.getCourse().getCode() + " - " + section.getCourse().getName()
                : "Unknown Course";
        var aiSummaryDto = feedbackAiService.summarizeComments(sectionId, commentList, courseName, teacherName);
        boolean useKhmer = "km".equalsIgnoreCase(lang);

        if (useKhmer && aiSummaryDto != null) {
            // Auto-translate to Khmer if not cached; translateToKhmer returns cached or calls Claude
            var khmerDto = feedbackAiService.translateToKhmer(sectionId, aiSummaryDto);
            if (khmerDto != null) {
                params.put("aiSummary", addKhmerBreaks(
                        khmerDto.getSummary() != null ? khmerDto.getSummary() : "-", 28));
                params.put("aiStrengthsLines", khmerDto.getStrengths() != null ? khmerDto.getStrengths() : List.of());
                params.put("aiStrengths", addKhmerBreaks(khmerDto.getStrengths() != null
                        ? khmerDto.getStrengths().stream().map(s -> "• " + s).collect(Collectors.joining("\n"))
                        : "-", 28));
                params.put("aiImprovementsLines", khmerDto.getImprovements() != null ? khmerDto.getImprovements() : List.of());
                params.put("aiImprovements", addKhmerBreaks(khmerDto.getImprovements() != null
                        ? khmerDto.getImprovements().stream().map(s -> "• " + s).collect(Collectors.joining("\n"))
                        : "-", 28));
                params.put("aiRecommendation", addKhmerBreaks(
                        khmerDto.getRecommendation() != null ? khmerDto.getRecommendation() : "-", 28));
            } else {
                params.put("aiSummary", "-");
                params.put("aiStrengths", "-");
                params.put("aiImprovements", "-");
                params.put("aiRecommendation", "-");
            }
        } else if (aiSummaryDto != null) {
            params.put("aiSummary", aiSummaryDto.getSummary() != null ? aiSummaryDto.getSummary() : "-");
            params.put("aiStrengthsLines", aiSummaryDto.getStrengths() != null ? aiSummaryDto.getStrengths() : List.of());
            params.put("aiStrengths", aiSummaryDto.getStrengths() != null
                    ? aiSummaryDto.getStrengths().stream().map(s -> "• " + s).collect(Collectors.joining("\n"))
                    : "-");
            params.put("aiImprovementsLines", aiSummaryDto.getImprovements() != null ? aiSummaryDto.getImprovements() : List.of());
            params.put("aiImprovements", aiSummaryDto.getImprovements() != null
                    ? aiSummaryDto.getImprovements().stream().map(s -> "• " + s).collect(Collectors.joining("\n"))
                    : "-");
            params.put("aiRecommendation", aiSummaryDto.getRecommendation() != null ? aiSummaryDto.getRecommendation() : "-");
        } else {
            params.put("aiSummary", "-");
            params.put("aiStrengths", "-");
            params.put("aiImprovements", "-");
            params.put("aiRecommendation", "-");
        }

        params.put("count", String.valueOf(count));
        params.put("enrolled", String.valueOf(enrolled));
        params.put("responded", String.valueOf(responded));
        params.put("notResponded", String.valueOf(notResponded));
        params.put("responseRateText", responseRate == null ? "-" : String.format(Locale.US, "%.2f%%", responseRate));
        params.put("overallText", overall == null ? "-" : String.format(Locale.US, "%.2f", overall));
        params.put("generatedAt", generatedAt);

        // Khmer date for signature block  e.g. "រាជធានីភ្នំពេញ, ថ្ងៃទី ១៧ ខែ ០៣ ឆ្នាំ ២០២៦"
        java.time.LocalDate today = java.time.LocalDate.now();
        String khmerDate = "រាជធានីភ្នំពេញ, ថ្ងៃទី "
                + toKhmerDigits(String.format("%02d", today.getDayOfMonth()))
                + " ខែ "
                + toKhmerDigits(String.format("%02d", today.getMonthValue()))
                + " ឆ្នាំ "
                + toKhmerDigits(String.valueOf(today.getYear()));
        params.put("khmerDate", khmerDate);

        String filename = "section_report_" + safeFilename(section.getTeacher().getUsername()) + ".pdf";

        if (useKhmer) {
            return renderKhmerHtmlPdf(filename, params, categories);
        }

        // English PDF — same visual format as Khmer, English labels
        List<PdfCategoryVm> enCategories = List.of(
                buildCatVm("A. Teacher's Knowledge and Skills",          1, 10, qStats),
                buildCatVm("B. Providing Study Materials to Students",   11, 13, qStats),
                buildCatVm("C. Compliance with University Policies",     14, 18, qStats),
                buildCatVm("D. Teacher-Student Relationships",           19, 21, qStats),
                buildCatVm("E. Professional Ethics",                     22, 26, qStats));

        return renderEnHtmlPdf(filename, params, enCategories);
    }

        @GetMapping(value = "/{teacherId}/teacher-self/export.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
        public ResponseEntity<byte[]> exportTeacherSelfPdf(@PathVariable Long teacherId,
            @RequestParam Long semesterId,
            @RequestParam(defaultValue = "en") String lang) {

        var kind = EvaluationKind.TEACHER_SELF;
        var semester = semesterRepo.findById(semesterId).orElseThrow();
        var qStats = answerRepo.adminTeacherSelfQuestionStats(semesterId, teacherId, kind);
        List<String> commentList = answerRepo.adminTeacherSelfComments(semesterId, teacherId, kind).stream()
            .filter(c -> c != null && !c.isBlank())
            .map(String::trim)
            .distinct()
            .limit(30)
            .toList();
        String comments = commentList.isEmpty()
            ? "-"
            : commentList.stream().collect(Collectors.joining("\n• ", "• ", ""));

        long count = submissionRepo.countByKindAndSemester_IdAndSectionIsNullAndSubmittedBy_Id(kind, semesterId, teacherId);
        var submission = submissionRepo.findByKindAndSemester_IdAndSectionIsNullAndSubmittedBy_Id(kind, semesterId, teacherId)
            .orElse(null);
        long eligible = 1L;
        long notSubmitted = Math.max(0, eligible - count);
        Double responseRate = count > 0 ? 100.0 : 0.0;
        Double overall = avgRange(qStats, 1, 999);

        String teacherName = findTeacherDisplayNamesBySemester(semesterId).getOrDefault(
            teacherId,
            answerRepo.adminTeacherSelfReport(semesterId, kind).stream()
                .filter(r -> Objects.equals(r.sectionId(), teacherId))
                .map(AdminSectionReportRow::teacherUsername)
                .findFirst()
                .orElse("Teacher #" + teacherId));

        Map<String, Object> params = new HashMap<>();
        params.put("teacherName", teacherName);
        params.put("teachingClass", "Semester-level self-assessment");
        params.put("cohort", "-");
        params.put("promotion", "-");
        params.put("shiftTime", "-");
        params.put("semesterName", firstNonBlank(semester.getName(), "-"));
        params.put("course", "Teacher Self-Assessment");
        params.put("room", "-");
        params.put("comments", comments);
        LocalDateTime effectiveDateTime = submission != null && submission.getSubmittedAt() != null
            ? submission.getSubmittedAt()
            : LocalDateTime.now();
        params.put("submittedAtText", buildEnglishDate(effectiveDateTime.toLocalDate()));
        params.put("submittedAtKhmerText", buildKhmerDate(effectiveDateTime.toLocalDate()));
        params.put("count", String.valueOf(count));
        params.put("enrolled", String.valueOf(eligible));
        params.put("responded", String.valueOf(count));
        params.put("notResponded", String.valueOf(notSubmitted));
        params.put("responseRateText", responseRate == null ? "-" : String.format(Locale.US, "%.2f%%", responseRate));
        params.put("overallText", overall == null ? "-" : String.format(Locale.US, "%.2f", overall));
        params.put("generatedAt", buildEnglishDate(LocalDate.now()));
        params.put("khmerDate", buildKhmerDate(LocalDate.now()));

        boolean useKhmer = "km".equalsIgnoreCase(lang);
        String filename = "teacher_self_assessment_" + safeFilename(teacherName) + "_semester_" + semesterId + ".pdf";

        if (useKhmer) {
                List<PdfCategoryVm> kmCategories = List.of(
                    buildCatVm(addKhmerBreaks("ក. សំណួរ Q1–Q5", 100), 1, 5, qStats),
                    buildCatVm(addKhmerBreaks("ខ. សំណួរ Q6–Q10", 100), 6, 10, qStats),
                    buildCatVm(addKhmerBreaks("គ. សំណួរ Q11–Q15", 100), 11, 15, qStats),
                    buildCatVm(addKhmerBreaks("ឃ. សំណួរ Q16–Q20", 100), 16, 20, qStats));
            return renderKhmerHtmlPdf("admin/report_detail_teacher_self_km_pdf", filename, params, kmCategories);
        }

        List<PdfCategoryVm> enCategories = List.of(
                buildCatVm("A. Questions Q1-Q5", 1, 5, qStats),
                buildCatVm("B. Questions Q6-Q10", 6, 10, qStats),
                buildCatVm("C. Questions Q11-Q15", 11, 15, qStats),
                buildCatVm("D. Questions Q16-Q20", 16, 20, qStats));
        return renderEnHtmlPdf("admin/report_detail_teacher_self_en_pdf", filename, params, enCategories);
        }

    // ========= Export PDF with Human Summary =========
    // @PostMapping(value = "/{sectionId}/export-human.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    // public ResponseEntity<byte[]> exportSectionPdfHuman(
    //         @PathVariable Long sectionId,
    //         @RequestParam(defaultValue = "en") String lang,
    //         @RequestParam(required = false, defaultValue = "") String humanSummaryText,
    //         @RequestParam(required = false, defaultValue = "") String humanStrengths,
    //         @RequestParam(required = false, defaultValue = "") String humanImprovements,
    //         @RequestParam(required = false, defaultValue = "") String humanRecommendation) {

    //     var kind = EvaluationKind.STUDENT_FEEDBACK;

    //     var section = sectionRepo.findById(sectionId).orElseThrow();
    //     var qStats = answerRepo.adminQuestionStats(sectionId, kind);
    //     var comments = answerRepo.adminComments(sectionId, kind)
    //             .stream()
    //             .filter(c -> c != null && !c.isBlank())
    //             .map(String::trim)
    //             .distinct()
    //             .limit(20)
    //             .collect(Collectors.joining("\n• ", "• ", ""));

    //     long count = submissionRepo.countByKindAndSectionId(kind, sectionId);
    //     long enrolled = enrollmentRepo.countBySection_Id(sectionId);
    //     Double responseRate = (enrolled == 0) ? null : (count * 100.0 / enrolled);
    //     Double overall = avgRange(qStats, 1, 999);
    //     long notResponded = Math.max(0, enrolled - count);

    //     String teacherName = firstNonBlank(
    //             safeStr(() -> reflectString(section.getTeacher(), "getFullName", "getName")),
    //             safeStr(() -> section.getTeacher().getUsername()),
    //             "-");

    //     String teachingClass = firstNonBlank(
    //             safeStr(() -> reflectString(section, "getTeachingClass", "getClassName", "getClassCode")),
    //             safeStr(() -> section.getSectionName()),
    //             "-");

    //     String cohort = firstNonBlank(
    //             safeStr(() -> section.getCohort() != null ? String.valueOf(section.getCohort().getCohortNo()) : null),
    //             "-");

    //     String promotion = firstNonBlank(
    //             safeStr(() -> section.getCohort() != null ? String.valueOf(section.getCohort().getCohortNo()) : null),
    //             "-");

    //     String shiftTime = firstNonBlank(safeStr(() -> String.valueOf(section.getShiftTime())), "-");

    //     String semesterName = firstNonBlank(
    //             safeStr(() -> section.getSemester() != null ? section.getSemester().getName() : null),
    //             "-");

    //     String course = firstNonBlank(
    //             safeStr(() -> section.getCourse() != null
    //                     ? section.getCourse().getCode() + " - " + section.getCourse().getName()
    //                     : null),
    //             "-");

    //     String room = firstNonBlank(
    //             safeStr(() -> (section.getBuilding() != null ? section.getBuilding() : "-") + "-"
    //                     + (section.getRoom() != null ? section.getRoom() : "-")),
    //             "-");

    //     // Build category rows same as AI export
    //     List<PdfCategoryVm> categories = List.of(
    //             buildCatVm(addKhmerBreaks("ក.ចំណេះដឹង និងជំនាញរបស់គ្រូបង្រៀន", 100), 1, 10, qStats),
    //             buildCatVm(addKhmerBreaks("ខ.ការផ្តល់ឯកសារសិក្សាដល់និស្សិត", 100), 11, 13, qStats),
    //             buildCatVm(addKhmerBreaks("គ.ការអនុវត្តតាមគោលការណ៍របស់សាកលវិទ្យាល័យ", 100), 14, 18, qStats),
    //             buildCatVm(addKhmerBreaks("ឃ.ទំនាក់ទំនងរវាងគ្រូបង្រៀនជាមួយនិស្សិត", 100), 19, 21, qStats),
    //             buildCatVm(addKhmerBreaks("ង.ក្រមសីលធម៌វិជ្ជាជីវៈ", 100), 22, 26, qStats));

    //     List<JasperRowVm> rows = new ArrayList<>();
    //     for (PdfCategoryVm cat : categories) {
    //         if (cat.getQuestions() == null || cat.getQuestions().isEmpty()) {
    //             rows.add(new JasperRowVm(
    //                     cat.getLabel(), cat.getRangeText(), cat.getAvgText(),
    //                     null, "No questions found in this category.", "-", "-", "-", "-"));
    //         } else {
    //             for (PdfQuestionVm q : cat.getQuestions()) {
    //                 String nText;
    //                 if (q.getN() == null) {
    //                     nText = "-";
    //                 } else if (enrolled <= 0) {
    //                     nText = String.valueOf(q.getN());
    //                 } else {
    //                     double pct = (q.getN() * 100.0) / enrolled;
    //                     nText = String.format(Locale.US, "%d (%.0f%%)", q.getN(), pct);
    //                 }
    //                 rows.add(new JasperRowVm(
    //                         cat.getLabel(), cat.getRangeText(), cat.getAvgText(),
    //                         q.getOrderNo(),
    //                         addKhmerBreaks(q.getQuestionText(), 45),
    //                         q.getMinScore() == null ? "-" : String.valueOf(q.getMinScore()),
    //                         q.getMaxScore() == null ? "-" : String.valueOf(q.getMaxScore()),
    //                         q.getAvgScore() == null ? "-" : String.format(Locale.US, "%.2f", q.getAvgScore()),
    //                         nText));
    //             }
    //         }
    //     }

    //     String generatedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

    //     Map<String, Object> params = new HashMap<>();
    //     params.put(JRParameter.REPORT_LOCALE, "km".equalsIgnoreCase(lang) ? Locale.forLanguageTag("km") : Locale.ENGLISH);

    //     params.put("teacherName", teacherName);
    //     params.put("teachingClass", teachingClass);
    //     params.put("cohort", cohort);
    //     params.put("promotion", promotion);
    //     params.put("shiftTime", shiftTime);
    //     params.put("semesterName", semesterName);
    //     params.put("course", course);
    //     params.put("room", room);
    //     params.put("comments", comments);
    //     params.put("aiStrengthsLines", splitBulletLines(humanStrengths));
    //     params.put("aiImprovementsLines", splitBulletLines(humanImprovements));

    //     // ===== Human Summary — use exactly what the admin typed =====
    //     boolean isKhmer = "km".equalsIgnoreCase(lang);

    //     // Strengths: split by newline, prefix each with "• "
    //     String strengthsFormatted = formatHumanBullets(humanStrengths, isKhmer);
    //     String improvementsFormatted = formatHumanBullets(humanImprovements, isKhmer);

    //     params.put("aiSummary",
    //             isKhmer ? addKhmerBreaks(blankOr(humanSummaryText), 28) : blankOr(humanSummaryText));
    //     params.put("aiStrengths",
    //             isKhmer ? addKhmerBreaks(strengthsFormatted, 28) : strengthsFormatted);
    //     params.put("aiImprovements",
    //             isKhmer ? addKhmerBreaks(improvementsFormatted, 28) : improvementsFormatted);
    //     params.put("aiRecommendation",
    //             isKhmer ? addKhmerBreaks(blankOr(humanRecommendation), 28) : blankOr(humanRecommendation));

    //     params.put("count", String.valueOf(count));
    //     params.put("enrolled", String.valueOf(enrolled));
    //     params.put("responded", String.valueOf(count));
    //     params.put("notResponded", String.valueOf(notResponded));
    //     params.put("responseRateText", responseRate == null ? "-" : String.format(Locale.US, "%.2f%%", responseRate));
    //     params.put("overallText", overall == null ? "-" : String.format(Locale.US, "%.2f", overall));
    //     params.put("generatedAt", generatedAt);

    //     java.time.LocalDate today = java.time.LocalDate.now();
    //     String khmerDate = "រាជធានីភ្នំពេញ, ថ្ងៃទី "
    //             + toKhmerDigits(String.format("%02d", today.getDayOfMonth()))
    //             + " ខែ "
    //             + toKhmerDigits(String.format("%02d", today.getMonthValue()))
    //             + " ឆ្នាំ "
    //             + toKhmerDigits(String.valueOf(today.getYear()));
    //     params.put("khmerDate", khmerDate);

    //     String humanFilename = "section_report_human_" + safeFilename(section.getTeacher().getUsername()) + ".pdf";

    //     if (isKhmer) {
    //         return renderKhmerHtmlPdf(humanFilename, params, categories);
    //     }

    //     // English PDF — same visual format as Khmer, English labels
    //     List<PdfCategoryVm> enCategories = List.of(
    //             buildCatVm("A. Teacher's Knowledge and Skills",          1, 10, qStats),
    //             buildCatVm("B. Providing Study Materials to Students",   11, 13, qStats),
    //             buildCatVm("C. Compliance with University Policies",     14, 18, qStats),
    //             buildCatVm("D. Teacher-Student Relationships",           19, 21, qStats),
    //             buildCatVm("E. Professional Ethics",                     22, 26, qStats));

    //     return renderEnHtmlPdf(humanFilename, params, enCategories);
    // }

    /** Returns "-" if the value is null/blank, otherwise the trimmed value. */
    private static String blankOr(String s) {
        return (s == null || s.isBlank()) ? "-" : s.trim();
    }

    private static String buildKhmerDate() {
        return buildKhmerDate(LocalDate.now());
    }

    private static String buildKhmerDate(LocalDate date) {
        String[] khmerMonths = {
                "មករា", "កុម្ភៈ", "មីនា", "មេសា", "ឧសភា", "មិថុនា",
                "កក្កដា", "សីហា", "កញ្ញា", "តុលា", "វិច្ឆិកា", "ធ្នូ"
        };
        return "រាជធានីភ្នំពេញ ថ្ងៃទី"
                + toKhmerDigits(String.valueOf(date.getDayOfMonth()))
                + " ខែ"
                + khmerMonths[date.getMonthValue() - 1]
                + " ឆ្នាំ"
                + toKhmerDigits(String.valueOf(date.getYear()));
    }

    private static String buildEnglishDate(LocalDate date) {
        return "Phnom Penh, " + date.format(DateTimeFormatter.ofPattern("d MMMM, yyyy", Locale.ENGLISH));
    }

    /**
     * Splits multi-line human input into bullet points.
     * Each non-blank line becomes "• line".
     */
    private static String formatHumanBullets(String raw, boolean isKhmer) {
        if (raw == null || raw.isBlank()) return "-";
        String joined = Arrays.stream(raw.split("\\r?\\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> "• " + s)
                .collect(Collectors.joining("\n"));
        return joined.isEmpty() ? "-" : joined;
    }

    private static String safeFilename(String value) {
        if (value == null || value.isBlank()) return "teacher";
        return value.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /** Row VM used by Jasper template */
    public static class JasperRowVm {
        private final String categoryLabel;
        private final String rangeText;
        private final String categoryAvgText;

        private final Integer orderNo;
        private final String questionText;
        private final String minText;
        private final String maxText;
        private final String avgText;
        private final String nText;

        public JasperRowVm(String categoryLabel, String rangeText, String categoryAvgText,
                Integer orderNo, String questionText,
                String minText, String maxText, String avgText, String nText) {
            this.categoryLabel = categoryLabel;
            this.rangeText = rangeText;
            this.categoryAvgText = categoryAvgText;
            this.orderNo = orderNo;
            this.questionText = questionText;
            this.minText = minText;
            this.maxText = maxText;
            this.avgText = avgText;
            this.nText = nText;
        }

        public String getCategoryLabel() {
            return categoryLabel;
        }

        public String getRangeText() {
            return rangeText;
        }

        public String getCategoryAvgText() {
            return categoryAvgText;
        }

        public Integer getOrderNo() {
            return orderNo;
        }

        public String getQuestionText() {
            return questionText;
        }

        public String getMinText() {
            return minText;
        }

        public String getMaxText() {
            return maxText;
        }

        public String getAvgText() {
            return avgText;
        }

        public String getNText() {
            return nText;
        }
    }

    private static PdfCategoryVm buildCatVm(String label, int start, int end, List<AdminQuestionScoreStat> all) {
        List<PdfQuestionVm> qs = all.stream()
                .filter(q -> q.orderNo() != null && q.orderNo() >= start && q.orderNo() <= end)
                .sorted(Comparator.comparing(AdminQuestionScoreStat::orderNo))
                .map(PdfQuestionVm::from)
                .toList();

        Double avg = avgRange(all, start, end);
        String avgText = (avg == null) ? "-" : String.format(Locale.US, "%.2f", avg);
        String rangeText = "(Q" + start + "–Q" + end + ")";

        return new PdfCategoryVm(label, start, end, rangeText, avgText, qs);
    }

    private static String reflectString(Object target, String... methodNames) {
        if (target == null)
            return null;
        for (String m : methodNames) {
            try {
                var method = target.getClass().getMethod(m);
                Object val = method.invoke(target);
                if (val != null) {
                    String s = val.toString().trim();
                    if (!s.isEmpty())
                        return s;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static String safeStr(Supplier<String> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            return null;
        }
    }

    private static String firstNonBlank(String... xs) {
        for (String x : xs) {
            if (x != null && !x.trim().isEmpty())
                return x.trim();
        }
        return null;
    }

    private ResponseEntity<byte[]> renderKhmerHtmlPdf(String filename, Map<String, Object> params,
            List<PdfCategoryVm> categories) {
        return renderKhmerHtmlPdf("admin/report_detail_km_pdf", filename, params, categories);
        }

        private ResponseEntity<byte[]> renderKhmerHtmlPdf(String templateName, String filename, Map<String, Object> params,
            List<PdfCategoryVm> categories) {
        try {
            Map<String, Object> model = new HashMap<>(params);
            model.put("teacherName", sanitizeKhmerHtmlText((String) model.get("teacherName")));
            model.put("teachingClass", sanitizeKhmerHtmlText((String) model.get("teachingClass")));
            model.put("cohort", sanitizeKhmerHtmlText((String) model.get("cohort")));
            model.put("promotion", sanitizeKhmerHtmlText((String) model.get("promotion")));
            model.put("shiftTime", sanitizeKhmerHtmlText((String) model.get("shiftTime")));
            model.put("semesterName", sanitizeKhmerHtmlText((String) model.get("semesterName")));
            model.put("course", sanitizeKhmerHtmlText((String) model.get("course")));
            model.put("room", sanitizeKhmerHtmlText((String) model.get("room")));
            model.put("aiSummary", sanitizeKhmerHtmlText((String) model.get("aiSummary")));
            model.put("aiRecommendation", sanitizeKhmerHtmlText((String) model.get("aiRecommendation")));
            model.put("khmerDate", sanitizeKhmerHtmlText((String) model.get("khmerDate")));
            model.put("aiStrengthsLines", sanitizeKhmerHtmlLines((List<String>) model.get("aiStrengthsLines")));
            model.put("aiImprovementsLines", sanitizeKhmerHtmlLines((List<String>) model.get("aiImprovementsLines")));
            model.put("categories", sanitizeKhmerCategories(categories));
            model.put("preparedBy", sanitizeKhmerHtmlText("សាស្ត្រ.ជំនួយ ស្រេង វិចិត្រ"));
                model.put("khmerOsSiemreapUri", resourceFileUri("classpath:fonts/KhmerOSsiemreap.ttf"));
                model.put("khmerOsMuolLightUri", resourceFileUri("classpath:fonts/KhmerOSmuollight.ttf"));
                model.put("notoSansKhmerUri", resourceFileUri("classpath:fonts/NotoSansKhmer.ttf"));

            Context context = new Context(Locale.forLanguageTag("km"));
            context.setVariables(model);

            String html = templateEngine.process(templateName, context);
                byte[] pdfBytes = renderHtmlPdfWithChrome(html);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdfBytes);
        } catch (Exception e) {
            throw new RuntimeException("PDF export failed (Khmer HTML): " + e.getMessage(), e);
        }
    }

    private ResponseEntity<byte[]> renderEnHtmlPdf(String filename, Map<String, Object> params,
            List<PdfCategoryVm> categories) {
        return renderEnHtmlPdf("admin/report_detail_en_pdf", filename, params, categories);
        }

        private ResponseEntity<byte[]> renderEnHtmlPdf(String templateName, String filename, Map<String, Object> params,
            List<PdfCategoryVm> categories) {
        try {
            Map<String, Object> model = new HashMap<>(params);
            model.put("categories", categories);
            model.put("preparedBy", "Assist. Prof. Sreng Vichet");

            Context context = new Context(Locale.ENGLISH);
            context.setVariables(model);

            String html = templateEngine.process(templateName, context);
            byte[] pdfBytes = renderHtmlPdfWithChrome(html);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdfBytes);
        } catch (Exception e) {
            throw new RuntimeException("PDF export failed (English HTML): " + e.getMessage(), e);
        }
    }

    private byte[] renderHtmlPdf(String html) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.useFastMode();
        builder.toStream(output);
        builder.withHtmlContent(html, null);
        builder.useFont(() -> AdminReportController.class.getResourceAsStream("/fonts/NotoSansKhmer.ttf"), "NotoSansKhmer");
        builder.useFont(() -> AdminReportController.class.getResourceAsStream("/fonts/KhmerOSsiemreap.ttf"), "Khmer OS Siemreap");
        builder.useFont(() -> AdminReportController.class.getResourceAsStream("/fonts/KhmerOSmuollight.ttf"), "Khmer OS Muol Light");
        builder.run();
        return output.toByteArray();
    }

    private byte[] renderHtmlPdfWithChrome(String html) throws Exception {
        String chromePath = findChromeExecutable();
        if (chromePath == null) {
            return renderHtmlPdf(html);
        }

        Path tempDir = Files.createTempDirectory("khmer-pdf-");
        Path htmlFile = tempDir.resolve("report.html");
        Path pdfFile = tempDir.resolve("report.pdf");

        try {
            Files.writeString(htmlFile, html, StandardCharsets.UTF_8);

            ProcessBuilder processBuilder = new ProcessBuilder(
                    chromePath,
                    "--headless=new",
                    "--disable-gpu",
                    "--no-first-run",
                    "--no-default-browser-check",
                    "--allow-file-access-from-files",
                    "--print-to-pdf-no-header",
                    "--print-to-pdf=" + pdfFile.toAbsolutePath(),
                    htmlFile.toUri().toString());
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();

            if (exitCode != 0 || !Files.exists(pdfFile)) {
                throw new RuntimeException("Chrome PDF rendering failed: " + output.trim());
            }

            return Files.readAllBytes(pdfFile);
        } finally {
            try {
                Files.deleteIfExists(htmlFile);
                Files.deleteIfExists(pdfFile);
                Files.deleteIfExists(tempDir);
            } catch (Exception ignored) {
            }
        }
    }

    private static String findChromeExecutable() {
        String[] candidates = new String[] {
                System.getenv("ProgramFiles(x86)") + "\\Google\\Chrome\\Application\\chrome.exe",
                System.getenv("ProgramFiles") + "\\Google\\Chrome\\Application\\chrome.exe",
                System.getenv("ProgramFiles(x86)") + "\\Microsoft\\Edge\\Application\\msedge.exe",
                System.getenv("ProgramFiles") + "\\Microsoft\\Edge\\Application\\msedge.exe"
        };
        for (String candidate : candidates) {
            if (candidate != null && Files.exists(Path.of(candidate))) {
                return candidate;
            }
        }
        return null;
    }

    private static String resourceFileUri(String location) {
        try {
            return ResourceUtils.getFile(location).toURI().toString();
        } catch (Exception e) {
            throw new RuntimeException("Unable to resolve resource URI: " + location, e);
        }
    }

    private static byte[] exportPdf(JasperPrint print) throws JRException {
        print.setProperty("net.sf.jasperreports.export.pdf.force.linebreak.policy", "true");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        JRPdfExporter exporter = new JRPdfExporter();
        exporter.setExporterInput(new SimpleExporterInput(print));
        exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(output));
        exporter.exportReport();
        return output.toByteArray();
    }

    /**
     * Inserts zero-width spaces (U+200B) before Khmer syllable-start characters
     * once {@code chunkSize} Khmer characters accumulate, so JasperReports can
     * line-wrap without breaking mid-syllable.
     *
     * Safe break points: base consonants (U+1780–U+17A2) and independent vowels
     * (U+17A3–U+17B3) only.  Excluded from break points:
     *   - U+17B4/U+17B5 (inherent vowels – invisible combining characters)
     *   - U+17B6–U+17FF (dependent vowels, diacritics, coeng, punctuation)
     *   - Any consonant immediately after coeng U+17D2 (subscript cluster member)
     */
    private static String addKhmerBreaks(String text, int chunkSize) {
        if (text == null) return null;
        StringBuilder sb = new StringBuilder(text.length() + 32);
        int run = 0;
        char prev = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean isKhmer = c >= '\u1780' && c <= '\u17FF';
            // U+1780–U+17B3: base consonants + independent vowels (true syllable starters).
            // U+17B4 and U+17B5 are inherent (combining) vowels — excluded.
            boolean isSyllableStart = c >= '\u1780' && c <= '\u17B3' && prev != '\u17D2';

            if (run >= chunkSize && isSyllableStart) {
                sb.append('\u200B');
                run = 0;
            }

            sb.append(c);
            prev = c;

            if (isKhmer) {
                run++;
            } else if (c == ' ' || c == '\n') {
                run = 0;
            }
        }
        return sb.toString();
    }

    private static String sanitizeKhmerHtmlText(String text) {
        if (text == null) {
            return null;
        }
        String cleaned = text
                .replace("\u200B", "")
                .replace("\uFEFF", "")
                .replace("\r\n", "\n");
        return Normalizer.normalize(cleaned, Normalizer.Form.NFC);
    }

    private static List<String> sanitizeKhmerHtmlLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        return lines.stream()
                .map(AdminReportController::sanitizeKhmerBulletLine)
                .filter(s -> !s.isBlank())
                .toList();
    }

    private static String sanitizeKhmerBulletLine(String text) {
        String cleaned = sanitizeKhmerHtmlText(text);
        if (cleaned == null) {
            return "";
        }
        return cleaned
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replaceFirst("^[•●▪*\\-]\\s*", "")
                .trim();
    }

    private static List<PdfCategoryVm> sanitizeKhmerCategories(List<PdfCategoryVm> categories) {
        if (categories == null || categories.isEmpty()) {
            return List.of();
        }
        return categories.stream()
                .map(cat -> new PdfCategoryVm(
                        sanitizeKhmerHtmlText(cat.getLabel()),
                        cat.getStart(),
                        cat.getEnd(),
                        sanitizeKhmerHtmlText(cat.getRangeText()),
                        sanitizeKhmerHtmlText(cat.getAvgText()),
                        cat.getQuestions() == null ? List.of() : cat.getQuestions().stream()
                                .map(q -> new PdfQuestionVm(
                                        q.getOrderNo(),
                                        sanitizeKhmerHtmlText(q.getQuestionText()),
                                        q.getMinScore(),
                                        q.getMaxScore(),
                                        q.getAvgScore(),
                                        q.getN()))
                                .toList()))
                .toList();
    }

    private String renderKhmerPanelImage(String title, List<String> rawLines, boolean bulletList) {
        List<String> lines = (rawLines == null ? List.<String>of() : rawLines).stream()
                .filter(Objects::nonNull)
                .map(bulletList ? AdminReportController::sanitizeKhmerBulletLine : AdminReportController::sanitizeKhmerHtmlText)
                .filter(s -> !s.isBlank() && !"-".equals(s.trim()))
                .toList();

        if (lines.isEmpty()) {
            lines = List.of("-");
        }

        try {
            Font bodyFont = loadFontResource("/fonts/KhmerOSsiemreap.ttf", 30f);
            Font headerFont = bodyFont.deriveFont(Font.BOLD, 28f);
            Font bulletFont = bodyFont.deriveFont(30f);

            int panelWidth = 1120;
            int headerHeight = 54;
            int outerPadding = 18;
            int innerPadding = 18;
            int contentWidth = panelWidth - (outerPadding + innerPadding) * 2;
            int lineGap = 8;
            float contentX = outerPadding + innerPadding;
            float paragraphGap = 10f;
            float bulletAreaWidth = bulletList ? 34f : 0f;
            float textWidth = contentWidth - bulletAreaWidth;

            BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            Graphics2D probeGraphics = probe.createGraphics();
            configureGraphics(probeGraphics);
            FontRenderContext frc = probeGraphics.getFontRenderContext();

            List<PanelTextRun> runs = new ArrayList<>();
            float y = headerHeight + innerPadding;

            for (int index = 0; index < lines.size(); index++) {
                String line = lines.get(index);
                y = appendPanelRuns(
                        runs,
                        line,
                        frc,
                        bodyFont,
                        bulletFont,
                        contentX,
                        textWidth,
                        y,
                        lineGap,
                        paragraphGap,
                        bulletList);
                if (index < lines.size() - 1) {
                    y += 2f;
                }
            }

            probeGraphics.dispose();

            int panelHeight = Math.max(headerHeight + 80, (int) Math.ceil(y + innerPadding));

            BufferedImage image = new BufferedImage(panelWidth, panelHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            configureGraphics(graphics);

            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, panelWidth, panelHeight);

            Color border = new Color(175, 194, 216);
            Color headerBg = new Color(219, 231, 244);
            Color headerText = new Color(32, 74, 122);
            Color bodyText = new Color(27, 31, 36);

            graphics.setColor(headerBg);
            graphics.fillRect(0, 0, panelWidth - 1, headerHeight);
            graphics.setColor(border);
            graphics.drawRect(0, 0, panelWidth - 1, panelHeight - 1);
            graphics.drawLine(0, headerHeight, panelWidth - 1, headerHeight);

            graphics.setFont(headerFont);
            graphics.setColor(headerText);
            float headerBaseline = 35f;
            graphics.drawString(title, outerPadding, headerBaseline);

            graphics.setFont(bodyFont);
            graphics.setColor(bodyText);
            for (PanelTextRun run : runs) {
                if (run.drawBullet()) {
                    graphics.fillOval(Math.round(run.bulletX()), Math.round(run.bulletY()), 10, 10);
                }
                run.layout().draw(graphics, run.textX(), run.baselineY());
            }

            graphics.dispose();

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException("Khmer panel image rendering failed: " + e.getMessage(), e);
        }
    }

    private static float appendPanelRuns(List<PanelTextRun> runs, String text, FontRenderContext frc, Font bodyFont,
            Font bulletFont, float contentX, float textWidth, float currentY, int lineGap, float paragraphGap,
            boolean drawBullet) {
        AttributedString attributed = new AttributedString(text);
        attributed.addAttribute(TextAttribute.FONT, bodyFont);
        AttributedCharacterIterator iterator = attributed.getIterator();
        LineBreakMeasurer measurer = new LineBreakMeasurer(iterator, frc);

        boolean firstLine = true;
        while (measurer.getPosition() < iterator.getEndIndex()) {
            TextLayout layout = measurer.nextLayout(textWidth);
            if (layout == null) {
                break;
            }
            currentY += layout.getAscent();
            float textX = contentX + (drawBullet ? 34f : 0f);
            float bulletX = contentX + 6f;
            float bulletY = currentY - (layout.getAscent() * 0.55f);
            runs.add(new PanelTextRun(layout, textX, currentY, drawBullet && firstLine, bulletX, bulletY));
            currentY += layout.getDescent() + layout.getLeading() + lineGap;
            firstLine = false;
        }
        return currentY + paragraphGap;
    }

    private record PanelTextRun(TextLayout layout, float textX, float baselineY, boolean drawBullet, float bulletX,
            float bulletY) {
    }

    private static Font loadFontResource(String resourcePath, float size) {
        try (InputStream input = AdminReportController.class.getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IllegalStateException("Font resource not found: " + resourcePath);
            }
            return Font.createFont(Font.TRUETYPE_FONT, input).deriveFont(size);
        } catch (Exception e) {
            throw new RuntimeException("Unable to load font: " + resourcePath, e);
        }
    }

    private static void configureGraphics(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    private static List<String> splitBulletLines(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split("\\r?\\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
            .map(s -> s.replaceFirst("^[•-]\\s*", ""))
                .toList();
    }

    private static String toKhmerDigits(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            sb.append(c >= '0' && c <= '9' ? (char) (c - '0' + '\u17E0') : c);
        }
        return sb.toString();
    }

    // ========= Helpers =========
    private static Double avgRange(List<AdminQuestionScoreStat> stats, int start,
            int end) {
        double sum = 0.0;
        int n = 0;
        for (var s : stats) {
            if (s.orderNo() == null || s.avgScore() == null)
                continue;
            if (s.orderNo() >= start && s.orderNo() <= end) {
                sum += s.avgScore();
                n++;
            }
        }
        return (n == 0) ? null : (sum / n);
    }

    private static String csv(String s) {
        if (s == null)
            return "\"\"";
        String x = s.replace("\"", "\"\"");
        return "\"" + x + "\"";
    }

    private static String num(Double d) {
        return d == null ? "" : String.format(Locale.US, "%.2f", d);
    }

    private static String formatSubmittedAt(LocalDateTime submittedAt) {
        if (submittedAt == null) {
            return null;
        }
        return submittedAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    private static String formatSubmittedAtKhmer(LocalDateTime submittedAt) {
        if (submittedAt == null) {
            return null;
        }
        return toKhmerDigits(submittedAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
    }

    private static void populateCategoryMetadata(Model model, EvaluationKind kind) {
        boolean isTeacherSelf = kind == EvaluationKind.TEACHER_SELF;
        model.addAttribute("showCategoryE", !isTeacherSelf);
        model.addAttribute("categoryALabel", "ក");
        model.addAttribute("categoryBLabel", "ខ");
        model.addAttribute("categoryCLabel", "គ");
        model.addAttribute("categoryDLabel", "ឃ");
        model.addAttribute("categoryELabel", "ង");
        model.addAttribute("categoryARange", isTeacherSelf ? "Q1–Q5" : "Q1–Q10");
        model.addAttribute("categoryBRange", isTeacherSelf ? "Q6–Q10" : "Q11–Q13");
        model.addAttribute("categoryCRange", isTeacherSelf ? "Q11–Q15" : "Q14–Q18");
        model.addAttribute("categoryDRange", isTeacherSelf ? "Q16–Q20" : "Q19–Q21");
        model.addAttribute("categoryERange", "Q22–Q26");
    }

    private Map<Long, String> findTeacherDisplayNamesBySemester(Long semesterId) {
        Map<Long, String> teacherNames = new LinkedHashMap<>();
        if (semesterId == null) {
            return teacherNames;
        }

        for (var section : sectionRepo.findBySemesterId(semesterId)) {
            var teacher = section.getTeacher();
            if (teacher == null || teacher.getId() == null) {
                continue;
            }
            teacherNames.putIfAbsent(teacher.getId(), teacherDisplayName(teacher));
        }
        return teacherNames;
    }

    private static String teacherDisplayName(UserAccount teacher) {
        if (teacher == null) {
            return "-";
        }
        if (teacher.getFullName() != null && !teacher.getFullName().isBlank()) {
            return teacher.getFullName();
        }
        return teacher.getUsername();
    }

    // Build per-teacher weighted averages (weight = section responses)
    private static List<AdminTeacherSummaryRow> buildTeacherSummaries(List<AdminSectionReportRow> rows,
            Map<Long, Long> enrolledMap) {
        class Agg {
            long responses = 0;
            long enrolled = 0;

            double wA = 0, wB = 0, wC = 0, wD = 0, wE = 0, wO = 0;
            double sA = 0, sB = 0, sC = 0, sD = 0, sE = 0, sO = 0;
        }

        Map<String, Agg> map = new HashMap<>();

        for (var r : rows) {
            String teacher = r.teacherUsername();
            Agg a = map.computeIfAbsent(teacher, k -> new Agg());

            long resp = r.responses() == null ? 0L : r.responses();
            long enrolled = enrolledMap.getOrDefault(r.sectionId(), 0L);

            a.responses += resp;
            a.enrolled += enrolled;

            if (resp > 0) {
                if (r.avgCatA() != null) {
                    a.sA += r.avgCatA() * resp;
                    a.wA += resp;
                }
                if (r.avgCatB() != null) {
                    a.sB += r.avgCatB() * resp;
                    a.wB += resp;
                }
                if (r.avgCatC() != null) {
                    a.sC += r.avgCatC() * resp;
                    a.wC += resp;
                }
                if (r.avgCatD() != null) {
                    a.sD += r.avgCatD() * resp;
                    a.wD += resp;
                }
                if (r.avgCatE() != null) {
                    a.sE += r.avgCatE() * resp;
                    a.wE += resp;
                }
                if (r.overallAvg() != null) {
                    a.sO += r.overallAvg() * resp;
                    a.wO += resp;
                }
            }
        }

        List<AdminTeacherSummaryRow> out = new ArrayList<>();
        for (var e : map.entrySet()) {
            Agg a = e.getValue();
            if (a.responses <= 0)
                continue;

            Double rate = (a.enrolled == 0) ? null : (a.responses * 100.0 / a.enrolled);

            out.add(new AdminTeacherSummaryRow(
                    e.getKey(),
                    a.responses,
                    a.enrolled,
                    rate,
                    a.wA == 0 ? null : a.sA / a.wA,
                    a.wB == 0 ? null : a.sB / a.wB,
                    a.wC == 0 ? null : a.sC / a.wC,
                    a.wD == 0 ? null : a.sD / a.wD,
                    a.wE == 0 ? null : a.sE / a.wE,
                    a.wO == 0 ? null : a.sO / a.wO));
        }
        return out;
    }

    private static <T> List<T> topBy(List<T> list, Function<T, Double> metric, int n) {
        return list.stream()
                .filter(x -> metric.apply(x) != null)
                .sorted(Comparator.comparing(metric).reversed())
                .limit(n)
                .toList();
    }

    private static <T> List<T> bottomBy(List<T> list, Function<T, Double> metric, int n) {
        return list.stream()
                .filter(x -> metric.apply(x) != null)
                .sorted(Comparator.comparing(metric))
                .limit(n)
                .toList();
    }

    @GetMapping("/section/{sectionId:\\d+}/responses")
    public String sectionResponses(@PathVariable Long sectionId, Model model) {

        var section = sectionRepo.findById(sectionId).orElseThrow();

        var statuses = enrollmentRepo.adminStudentFeedbackStatus(
                sectionId,
                EvaluationKind.STUDENT_FEEDBACK);

        long total = statuses.size();
        long submitted = statuses.stream()
                .filter(s -> s.getSubmittedAt() != null)
                .count();

        long pending = total - submitted;
        double rate = (total == 0) ? 0 : (submitted * 100.0 / total);
        model.addAttribute("rate", rate);

        model.addAttribute("section", section);
        model.addAttribute("statuses", statuses);

        model.addAttribute("total", total);
        model.addAttribute("submitted", submitted);
        model.addAttribute("pending", pending);

        return "admin/section_responses";
    }

    @GetMapping(value = "/section/{sectionId:\\d+}/responses/export", produces = "text/csv")
    public ResponseEntity<byte[]> exportStudentFeedbackStatus(@PathVariable Long sectionId) {

        var section = sectionRepo.findById(sectionId).orElseThrow();

        var statuses = enrollmentRepo.adminStudentFeedbackStatus(
                sectionId,
                EvaluationKind.STUDENT_FEEDBACK);

        StringBuilder sb = new StringBuilder();

        // UTF-8 BOM for Khmer
        sb.append('\uFEFF');

        sb.append("StudentID,Username,FullName,Email,Status,SubmittedAt\n");

        for (var s : statuses) {

            String status = s.getSubmittedAt() != null ? "Submitted" : "Not Submitted";

            sb.append(s.getStudentId()).append(',')
                    .append(csv(s.getStudentUsername())).append(',')
                    .append(csv(s.getFullName())).append(',')
                    .append(csv(s.getEmail())).append(',')
                    .append(status).append(',')
                    .append(s.getSubmittedAt() == null ? "" : s.getSubmittedAt())
                    .append("\n");
        }

        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=student_feedback_status_" + sectionId + ".csv")
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .body(bytes);
    }

    @GetMapping(value = "/section/{sectionId:\\d+}/responses/export-pending", produces = "text/csv")
    public ResponseEntity<byte[]> exportPendingOnly(@PathVariable Long sectionId) {

        var kind = EvaluationKind.STUDENT_FEEDBACK;
        var section = sectionRepo.findById(sectionId).orElseThrow();
        // System.out.println("Group Num : " + section.getGroupNo());
        // 1) All enrolled students in this section
        var enrollments = enrollmentRepo.findBySection_Id(sectionId);

        // 2) Submissions for this section/kind -> map by studentId
        var submissions = submissionRepo.findBySection_IdAndKind(sectionId, kind)
                .stream()
                .collect(Collectors.toMap(
                        s -> s.getSubmittedBy().getId(),
                        s -> s));

        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF'); // ✅ UTF-8 BOM for Khmer Excel

        sb.append("studentLogin,fullName,cohortNo,groupNo,className,shiftTime,email,phone,status\n");

        // Section-level info (same for all students)
        String cohortNo = (section.getCohort() != null) ? String.valueOf(section.getCohort().getCohortNo()) : "";
        // String groupNo = (section.getGroupNo() != null) ?
        // String.valueOf(section.getGroupNo()) : "";
        String className = section.getSectionName() != null ? section.getSectionName() : "";
        String shiftTime = section.getShiftTime() != null ? section.getShiftTime().toString() : "";
        String groupNo = "";
        String fullName = "";

        for (var e : enrollments) {
            var student = e.getStudent();
            if (student == null)
                continue;

            boolean submitted = submissions.containsKey(student.getId());
            if (submitted)
                continue; // ✅ pending only
            var registry = studentRegistryRepo.findByStudentLogin(student.getUsername());

            if (registry.isPresent()) {
                groupNo = String.valueOf(registry.get().getGroupNo());
                fullName = registry.get().getFullName();
            }
            sb.append(csv(student.getUsername())).append(',')
                    .append(csv(fullName)).append(',')
                    .append(csv(cohortNo)).append(',')
                    .append(csv(groupNo)).append(',')
                    .append(csv(className)).append(',')
                    .append(csv(shiftTime)).append(',')
                    .append(csv(student.getEmail())).append(',')
                    .append(csv(student.getPhone())).append(',')
                    .append("Not Submitted")
                    .append('\n');
        }

        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=pending_students_section_" + sectionId + ".csv")
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .body(bytes);
    }

    @GetMapping(value = "/section/{sectionId:\\d+}/responses/export-all", produces = "text/csv")
    public ResponseEntity<byte[]> exportAllStudentsFeedbackStatus(@PathVariable Long sectionId) {

        var kind = EvaluationKind.STUDENT_FEEDBACK;
        var section = sectionRepo.findById(sectionId).orElseThrow();

        var enrollments = enrollmentRepo.findBySection_Id(sectionId);

        var submissions = submissionRepo
                .findBySection_IdAndKind(sectionId, EvaluationKind.STUDENT_FEEDBACK)
                .stream()
                .collect(Collectors.toMap(
                        s -> s.getSubmittedBy().getId(),
                        s -> s));

        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF'); // ✅ Khmer Excel
        sb.append("studentLogin,fullName,cohortNo,groupNo,className,shiftTime,email,phone,status,submittedAt\n");

        String cohortNo = (section.getCohort() != null) ? String.valueOf(section.getCohort().getCohortNo()) : "";
        String groupNo = (section.getGroupNo() != null) ? String.valueOf(section.getGroupNo()) : "";
        String className = section.getSectionName() != null ? section.getSectionName() : "";
        String shiftTime = section.getShiftTime() != null ? section.getShiftTime().toString() : "";

        for (var e : enrollments) {
            var student = e.getStudent();
            if (student == null)
                continue;

            var sub = submissions.get(student.getId());
            boolean submitted = (sub != null);

            sb.append(csv(student.getUsername())).append(',')
                    .append(csv(student.getFullName())).append(',')
                    .append(csv(cohortNo)).append(',')
                    .append(csv(groupNo)).append(',')
                    .append(csv(className)).append(',')
                    .append(csv(shiftTime)).append(',')
                    .append(csv(student.getEmail())).append(',')
                    .append(csv(student.getPhone())).append(',')
                    .append(submitted ? "Submitted" : "Not Submitted").append(',')
                    .append(csv(submitted ? String.valueOf(sub.getSubmittedAt()) : ""))
                    .append('\n');
        }

        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=all_students_feedback_status_section_" + sectionId + ".csv")
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .body(bytes);
    }
    // it is ok
    // @GetMapping(value = "/section/{sectionId:\\d+}/responses/export-pending",
    // produces = "text/csv")
    // public ResponseEntity<byte[]> exportPendingOnly(@PathVariable Long sectionId)
    // {

    // var statuses = enrollmentRepo.adminStudentFeedbackStatus(sectionId,
    // EvaluationKind.STUDENT_FEEDBACK);

    // StringBuilder sb = new StringBuilder();
    // sb.append('\uFEFF'); // UTF-8 BOM for Khmer Excel
    // sb.append("StudentID,Username,FullName,Email,Phone,Status\n");

    // for (var s : statuses) {
    // if (s.getSubmittedAt() != null)
    // continue; // pending only
    // sb.append(s.getStudentId()).append(',')
    // .append(csv(s.getStudentUsername())).append(',')
    // .append(csv(s.getFullName())).append(',')
    // .append(csv(s.getEmail())).append(',')
    // // .append(csv(s.getPhone())) // if you store phone (recommended)
    // .append(",Not Submitted\n");
    // }

    // byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);

    // return ResponseEntity.ok()
    // .header(HttpHeaders.CONTENT_DISPOSITION,
    // "attachment; filename=pending_students_" + sectionId + ".csv")
    // .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
    // .body(bytes);
    // }
}










// package kh.edu.num.feedback.web.admin;

// import kh.edu.num.feedback.domain.entity.EvaluationKind;
// import kh.edu.num.feedback.domain.repo.AnswerRepository;
// import kh.edu.num.feedback.domain.repo.ClassSectionRepository;
// import kh.edu.num.feedback.domain.repo.EnrollmentRepository;
// import kh.edu.num.feedback.domain.repo.SemesterRepository;
// import kh.edu.num.feedback.domain.repo.StudentRegistryRepository;
// import kh.edu.num.feedback.domain.repo.SubmissionRepository;
// import kh.edu.num.feedback.web.admin.dto.AdminQuestionScoreStat;
// import kh.edu.num.feedback.web.admin.dto.AdminSectionReportRow;
// import kh.edu.num.feedback.web.admin.dto.AdminTeacherSummaryRow;
// import kh.edu.num.feedback.domain.repo.AiFeedbackSummaryRepository;
// import kh.edu.num.feedback.web.ai.FeedbackAiService;
// import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;

// import org.springframework.http.HttpHeaders;
// import org.springframework.http.MediaType;
// import org.springframework.http.ResponseEntity;
// import org.springframework.stereotype.Controller;
// import org.springframework.ui.Model;
// import org.springframework.util.ResourceUtils;
// import org.springframework.web.bind.annotation.*;
// import org.thymeleaf.spring6.SpringTemplateEngine;

// import java.io.File;
// import java.nio.charset.StandardCharsets;
// import java.time.LocalDateTime;
// import java.time.format.DateTimeFormatter;
// import java.util.*;
// import java.util.function.Function;
// import java.util.function.Supplier;
// import java.util.stream.Collectors;
// import net.sf.jasperreports.engine.*;

// @Controller
// @RequestMapping("/admin/reports")
// public class AdminReportController {

//     private final SemesterRepository semesterRepo;
//     private final ClassSectionRepository sectionRepo;
//     private final SubmissionRepository submissionRepo;
//     private final AnswerRepository answerRepo;
//     private final EnrollmentRepository enrollmentRepo;
//     private final SpringTemplateEngine templateEngine;
//     private final StudentRegistryRepository studentRegistryRepo;
//     // private final FeedbackAiService feedbackAiService;
//     private final FeedbackAiService feedbackAiService;
//     private final AiFeedbackSummaryRepository aiSummaryRepo;

//     public AdminReportController(SemesterRepository semesterRepo,
//             ClassSectionRepository sectionRepo,
//             SubmissionRepository submissionRepo,
//             AnswerRepository answerRepo,
//             EnrollmentRepository enrollmentRepo,
//             SpringTemplateEngine templateEngine,
//             StudentRegistryRepository studentRegistryRepo,
//             FeedbackAiService feedbackAiService,
//             AiFeedbackSummaryRepository aiSummaryRepo) {
//         this.semesterRepo = semesterRepo;
//         this.sectionRepo = sectionRepo;
//         this.submissionRepo = submissionRepo;
//         this.answerRepo = answerRepo;
//         this.enrollmentRepo = enrollmentRepo;
//         this.templateEngine = templateEngine;
//         this.studentRegistryRepo = studentRegistryRepo;
//         this.feedbackAiService = feedbackAiService;
//         this.aiSummaryRepo = aiSummaryRepo;
//     }

//     // @GetMapping("/test-ai")
//     // @ResponseBody
//     // public Object testAi() {
//     // return feedbackAiService.summarizeComments(List.of(
//     // "The teacher explains clearly.",
//     // "More practical examples are needed.",
//     // "The teacher is friendly and helpful."));
//     // }

//     // ========= View Models (avoid Thymeleaf issues with record/projection access)
//     // =========
//     public static class SectionReportVm {
//         private final Long sectionId;
//         private final String teacherUsername;
//         private final String courseCode;
//         private final String courseName;
//         private final String shiftTime;
//         private final String building;
//         private final String room;
//         private final String sectionName;
//         private final String semesterName;

//         private final Long responses;
//         private final Long enrolled;
//         private final Double responseRate;

//         private final Double avgCatA;
//         private final Double avgCatB;
//         private final Double avgCatC;
//         private final Double avgCatD;
//         private final Double avgCatE;
//         private final Double overallAvg;

//         public SectionReportVm(Long sectionId,
//                 String teacherUsername,
//                 String courseCode,
//                 String courseName,
//                 String shiftTime,
//                 String building,
//                 String room,
//                 String sectionName,
//                 String semesterName,
//                 Long responses,
//                 Long enrolled,
//                 Double responseRate,
//                 Double avgCatA,
//                 Double avgCatB,
//                 Double avgCatC,
//                 Double avgCatD,
//                 Double avgCatE,
//                 Double overallAvg) {
//             this.sectionId = sectionId;
//             this.teacherUsername = teacherUsername;
//             this.courseCode = courseCode;
//             this.courseName = courseName;
//             this.shiftTime = shiftTime;
//             this.building = building;
//             this.room = room;
//             this.sectionName = sectionName;
//             this.semesterName = semesterName;
//             this.responses = responses;
//             this.enrolled = enrolled;
//             this.responseRate = responseRate;
//             this.avgCatA = avgCatA;
//             this.avgCatB = avgCatB;
//             this.avgCatC = avgCatC;
//             this.avgCatD = avgCatD;
//             this.avgCatE = avgCatE;
//             this.overallAvg = overallAvg;
//         }

//         public Long getSectionId() {
//             return sectionId;
//         }

//         public String getTeacherUsername() {
//             return teacherUsername;
//         }

//         public String getCourseCode() {
//             return courseCode;
//         }

//         public String getCourseName() {
//             return courseName;
//         }

//         public String getShiftTime() {
//             return shiftTime;
//         }

//         public String getBuilding() {
//             return building;
//         }

//         public String getRoom() {
//             return room;
//         }

//         public String getSectionName() {
//             return sectionName;
//         }

//         public String getSemesterName() {
//             return semesterName;
//         }

//         public Long getResponses() {
//             return responses;
//         }

//         public Long getEnrolled() {
//             return enrolled;
//         }

//         public Double getResponseRate() {
//             return responseRate;
//         }

//         public Double getAvgCatA() {
//             return avgCatA;
//         }

//         public Double getAvgCatB() {
//             return avgCatB;
//         }

//         public Double getAvgCatC() {
//             return avgCatC;
//         }

//         public Double getAvgCatD() {
//             return avgCatD;
//         }

//         public Double getAvgCatE() {
//             return avgCatE;
//         }

//         public Double getOverallAvg() {
//             return overallAvg;
//         }
//     }

//     public static class TeacherSummaryVm {
//         private final String teacherUsername;
//         private final Long responses;
//         private final Long enrolled;
//         private final Double responseRate;
//         private final Double avgCatA;
//         private final Double avgCatB;
//         private final Double avgCatC;
//         private final Double avgCatD;
//         private final Double avgCatE;
//         private final Double overallAvg;

//         public TeacherSummaryVm(String teacherUsername, Long responses, Long enrolled, Double responseRate,
//                 Double avgCatA, Double avgCatB, Double avgCatC, Double avgCatD, Double avgCatE, Double overallAvg) {
//             this.teacherUsername = teacherUsername;
//             this.responses = responses;
//             this.enrolled = enrolled;
//             this.responseRate = responseRate;
//             this.avgCatA = avgCatA;
//             this.avgCatB = avgCatB;
//             this.avgCatC = avgCatC;
//             this.avgCatD = avgCatD;
//             this.avgCatE = avgCatE;
//             this.overallAvg = overallAvg;
//         }

//         public static TeacherSummaryVm from(AdminTeacherSummaryRow r) {
//             return new TeacherSummaryVm(
//                     r.teacherUsername(),
//                     r.responses(),
//                     r.enrolled(),
//                     r.responseRate(),
//                     r.avgCatA(),
//                     r.avgCatB(),
//                     r.avgCatC(),
//                     r.avgCatD(),
//                     r.avgCatE(),
//                     r.overallAvg());
//         }

//         public String getTeacherUsername() {
//             return teacherUsername;
//         }

//         public Long getResponses() {
//             return responses;
//         }

//         public Long getEnrolled() {
//             return enrolled;
//         }

//         public Double getResponseRate() {
//             return responseRate;
//         }

//         public Double getAvgCatA() {
//             return avgCatA;
//         }

//         public Double getAvgCatB() {
//             return avgCatB;
//         }

//         public Double getAvgCatC() {
//             return avgCatC;
//         }

//         public Double getAvgCatD() {
//             return avgCatD;
//         }

//         public Double getAvgCatE() {
//             return avgCatE;
//         }

//         public Double getOverallAvg() {
//             return overallAvg;
//         }
//     }

//     public static class QuestionStatVm {
//         private final Integer orderNo;
//         private final String questionText;
//         private final Integer minScore;
//         private final Integer maxScore;
//         private final Double avgScore;
//         private final Long n;

//         public QuestionStatVm(Integer orderNo, String questionText, Integer minScore, Integer maxScore, Double avgScore,
//                 Long n) {
//             this.orderNo = orderNo;
//             this.questionText = questionText;
//             this.minScore = minScore;
//             this.maxScore = maxScore;
//             this.avgScore = avgScore;
//             this.n = n;
//         }

//         public static QuestionStatVm from(AdminQuestionScoreStat s) {
//             return new QuestionStatVm(s.orderNo(), s.questionText(), s.minScore(), s.maxScore(), s.avgScore(), s.n());
//         }

//         public Integer getOrderNo() {
//             return orderNo;
//         }

//         public String getQuestionText() {
//             return questionText;
//         }

//         public Integer getMinScore() {
//             return minScore;
//         }

//         public Integer getMaxScore() {
//             return maxScore;
//         }

//         public Double getAvgScore() {
//             return avgScore;
//         }

//         public Long getN() {
//             return n;
//         }
//     }

//     // ========= Pages =========
//     @GetMapping
//     public String page(@RequestParam(required = false) Long semesterId, Model model) {

//         var semesters = semesterRepo.findAll();

//         Long resolvedSemesterId = semesterId;
//         if (resolvedSemesterId == null && !semesters.isEmpty()) {
//             resolvedSemesterId = semesters.get(0).getId();
//         }
//         final Long semId = resolvedSemesterId;

//         var selectedSemester = (semId == null) ? null
//                 : semesters.stream()
//                         .filter(s -> Objects.equals(s.getId(), semId))
//                         .findFirst()
//                         .orElse(null);

//         var kind = EvaluationKind.STUDENT_FEEDBACK;

//         List<AdminSectionReportRow> rawRows = (semId == null)
//                 ? Collections.emptyList()
//                 : answerRepo.adminSectionReport(semId, kind);

//         // enrolledMap: sectionId -> enrolled count
//         Map<Long, Long> enrolledMap = new HashMap<>();
//         if (semId != null) {
//             for (var it : enrollmentRepo.countEnrollmentsBySemesterGroupBySection(semId)) {
//                 enrolledMap.put(it.getSectionId(), it.getEnrolled());
//             }
//         }

//         // Build VM rows
//         List<SectionReportVm> rows = new ArrayList<>();
//         for (var r : rawRows) {
//             long enrolled = enrolledMap.getOrDefault(r.sectionId(), 0L);
//             long resp = (r.responses() == null) ? 0L : r.responses();
//             Double rate = (enrolled == 0) ? null : (resp * 100.0 / enrolled);

//             rows.add(new SectionReportVm(
//                     r.sectionId(),
//                     r.teacherUsername(),
//                     r.courseCode(),
//                     r.courseName(),
//                     String.valueOf(r.shiftTime()),
//                     r.building(),
//                     r.room(),
//                     r.sectionName(),
//                     r.semesterName(),
//                     resp,
//                     enrolled,
//                     rate,
//                     r.avgCatA(),
//                     r.avgCatB(),
//                     r.avgCatC(),
//                     r.avgCatD(),
//                     r.avgCatE(),
//                     r.overallAvg()));
//         }

//         long totalResponses = rows.stream().mapToLong(x -> x.getResponses() == null ? 0L : x.getResponses()).sum();
//         long totalEnrolled = rows.stream().mapToLong(x -> x.getEnrolled() == null ? 0L : x.getEnrolled()).sum();

//         Double overallResponseRate = (totalEnrolled == 0) ? null : (totalResponses * 100.0 / totalEnrolled);

//         // Teacher rankings
//         List<AdminTeacherSummaryRow> teacherSummariesRaw = buildTeacherSummaries(rawRows, enrolledMap);
//         List<TeacherSummaryVm> teacherSummaries = teacherSummariesRaw.stream()
//                 .map(TeacherSummaryVm::from)
//                 .toList();

//         model.addAttribute("topOverall", topBy(teacherSummaries, TeacherSummaryVm::getOverallAvg, 5));
//         model.addAttribute("bottomOverall", bottomBy(teacherSummaries, TeacherSummaryVm::getOverallAvg, 5));

//         model.addAttribute("topA", topBy(teacherSummaries, TeacherSummaryVm::getAvgCatA, 5));
//         model.addAttribute("bottomA", bottomBy(teacherSummaries, TeacherSummaryVm::getAvgCatA, 5));

//         model.addAttribute("topB", topBy(teacherSummaries, TeacherSummaryVm::getAvgCatB, 5));
//         model.addAttribute("bottomB", bottomBy(teacherSummaries, TeacherSummaryVm::getAvgCatB, 5));

//         model.addAttribute("topC", topBy(teacherSummaries, TeacherSummaryVm::getAvgCatC, 5));
//         model.addAttribute("bottomC", bottomBy(teacherSummaries, TeacherSummaryVm::getAvgCatC, 5));

//         model.addAttribute("topD", topBy(teacherSummaries, TeacherSummaryVm::getAvgCatD, 5));
//         model.addAttribute("bottomD", bottomBy(teacherSummaries, TeacherSummaryVm::getAvgCatD, 5));

//         model.addAttribute("topE", topBy(teacherSummaries, TeacherSummaryVm::getAvgCatE, 5));
//         model.addAttribute("bottomE", bottomBy(teacherSummaries, TeacherSummaryVm::getAvgCatE, 5));

//         // Model attributes
//         model.addAttribute("semesters", semesters);
//         model.addAttribute("semesterId", semId);
//         model.addAttribute("selectedSemester", selectedSemester);

//         model.addAttribute("rows", rows);
//         model.addAttribute("kind", kind);

//         model.addAttribute("totalResponses", totalResponses);
//         model.addAttribute("totalEnrolled", totalEnrolled);
//         model.addAttribute("overallResponseRate", overallResponseRate);
//         model.addAttribute("totalSections", rows.size());

//         return "admin/reports";
//     }

//     @GetMapping("/{sectionId}")
//     public String detail(@PathVariable Long sectionId, Model model) {
//         var kind = EvaluationKind.STUDENT_FEEDBACK;

//         var section = sectionRepo.findById(sectionId).orElseThrow();
//         var qStatsRaw = answerRepo.adminQuestionStats(sectionId, kind);
//         var comments = answerRepo.adminComments(sectionId, kind);
//         var count = submissionRepo.countByKindAndSectionId(kind, sectionId);

//         long enrolled = enrollmentRepo.countBySection_Id(sectionId);
//         Double responseRate = (enrolled == 0) ? null : (count * 100.0 / enrolled);

//         Double catA = avgRange(qStatsRaw, 1, 10);
//         Double catB = avgRange(qStatsRaw, 11, 13);
//         Double catC = avgRange(qStatsRaw, 14, 18);
//         Double catD = avgRange(qStatsRaw, 19, 21);
//         Double catE = avgRange(qStatsRaw, 22, 26);
//         Double overall = avgRange(qStatsRaw, 1, 999);

//         List<QuestionStatVm> qStats = qStatsRaw.stream().map(QuestionStatVm::from).toList();

//         model.addAttribute("section", section);
//         model.addAttribute("qStats", qStats);
//         model.addAttribute("comments", comments);
//         model.addAttribute("count", count);
//         model.addAttribute("enrolled", enrolled);
//         model.addAttribute("responseRate", responseRate);
//         model.addAttribute("catA", catA);
//         model.addAttribute("catB", catB);
//         model.addAttribute("catC", catC);
//         model.addAttribute("catD", catD);
//         model.addAttribute("catE", catE);
//         model.addAttribute("overall", overall);

//         return "admin/report_detail";
//     }

//     // Update getAiSummary to pass sectionId
//     @GetMapping("/{sectionId}/ai-summary")
//     @ResponseBody
//     public ResponseEntity<?> getAiSummary(@PathVariable Long sectionId) {
//         try {
//             var kind = EvaluationKind.STUDENT_FEEDBACK;
//             var section = sectionRepo.findById(sectionId).orElseThrow();
//             var comments = answerRepo.adminComments(sectionId, kind);

//             String courseName = (section.getCourse() != null)
//                     ? section.getCourse().getCode() + " - " + section.getCourse().getName()
//                     : "Unknown Course";
//             String teacherName = (section.getTeacher() != null)
//                     ? section.getTeacher().getUsername()
//                     : "Unknown Teacher";

//             // ✅ Pass sectionId so service checks DB first
//             var summary = feedbackAiService.summarizeComments(
//                     sectionId, comments, courseName, teacherName);

//             return ResponseEntity.ok(summary);
//         } catch (Exception e) {
//             return ResponseEntity.ok(null);
//         }
//     }

//     // ✅ New endpoint for Regenerate button
//     @PostMapping("/{sectionId}/ai-summary/regenerate")
//     @ResponseBody
//     public ResponseEntity<?> regenerateAiSummary(@PathVariable Long sectionId) {
//         try {
//             var kind = EvaluationKind.STUDENT_FEEDBACK;
//             var section = sectionRepo.findById(sectionId).orElseThrow();
//             var comments = answerRepo.adminComments(sectionId, kind);

//             String courseName = (section.getCourse() != null)
//                     ? section.getCourse().getCode() + " - " + section.getCourse().getName()
//                     : "Unknown Course";
//             String teacherName = (section.getTeacher() != null)
//                     ? section.getTeacher().getUsername()
//                     : "Unknown Teacher";

//             var summary = feedbackAiService.regenerateSummary(
//                     sectionId, comments, courseName, teacherName);

//             return ResponseEntity.ok(summary);
//         } catch (Exception e) {
//             return ResponseEntity.ok(null);
//         }
//     }

//     @PostMapping("/{sectionId}/ai-summary/translate")
//     @ResponseBody
//     public ResponseEntity<?> translateAiSummary(@PathVariable Long sectionId) {
//         try {
//             var kind = EvaluationKind.STUDENT_FEEDBACK;
//             var section = sectionRepo.findById(sectionId).orElseThrow();
//             var comments = answerRepo.adminComments(sectionId, kind);

//             String courseName = (section.getCourse() != null)
//                     ? section.getCourse().getCode() + " - " + section.getCourse().getName()
//                     : "Unknown Course";
//             String teacherName = (section.getTeacher() != null)
//                     ? section.getTeacher().getUsername()
//                     : "Unknown Teacher";

//             var englishSummary = feedbackAiService.summarizeComments(sectionId, comments, courseName, teacherName);
//             if (englishSummary == null)
//                 return ResponseEntity.badRequest().body(Map.of("error", "No English summary available to translate."));

//             var khmerSummary = feedbackAiService.translateToKhmer(sectionId, englishSummary);
//             if (khmerSummary == null)
//                 return ResponseEntity.badRequest().body(Map.of("error", "Translation failed. Check ANTHROPIC_API_KEY in launch.json."));

//             return ResponseEntity.ok(khmerSummary);
//         } catch (Exception e) {
//             return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
//         }
//     }

//     // ========= Export CSV (semester) =========
//     @GetMapping(value = "/export", produces = "text/csv")
//     public ResponseEntity<byte[]> exportCsv(@RequestParam Long semesterId) {
//         var kind = EvaluationKind.STUDENT_FEEDBACK;
//         List<AdminSectionReportRow> rows = answerRepo.adminSectionReport(semesterId, kind);

//         Map<Long, Long> enrolledMap = enrollmentRepo.countEnrollmentsBySemesterGroupBySection(semesterId)
//                 .stream()
//                 .collect(Collectors.toMap(
//                         EnrollmentRepository.SectionEnrollCount::getSectionId,
//                         EnrollmentRepository.SectionEnrollCount::getEnrolled));

//         StringBuilder sb = new StringBuilder();
//         sb.append("Teacher,CourseCode,CourseName,Shift,Building,Room,Section,Responses,Enrolled,ResponseRate%,")
//                 .append("CatA(Q1-10),CatB(Q11-13),CatC(Q14-18),CatD(Q19-21),CatE(Q22-26),Overall\n");

//         for (var r : rows) {
//             long enrolled = enrolledMap.getOrDefault(r.sectionId(), 0L);
//             long resp = r.responses() == null ? 0L : r.responses();
//             Double rate = (enrolled == 0) ? null : (resp * 100.0 / enrolled);

//             sb.append(csv(r.teacherUsername())).append(',')
//                     .append(csv(r.courseCode())).append(',')
//                     .append(csv(r.courseName())).append(',')
//                     .append(csv(String.valueOf(r.shiftTime()))).append(',')
//                     .append(csv(r.building())).append(',')
//                     .append(csv(r.room())).append(',')
//                     .append(csv(r.sectionName())).append(',')
//                     .append(resp).append(',')
//                     .append(enrolled).append(',')
//                     .append(rate == null ? "" : String.format(Locale.US, "%.2f", rate)).append(',')
//                     .append(num(r.avgCatA())).append(',')
//                     .append(num(r.avgCatB())).append(',')
//                     .append(num(r.avgCatC())).append(',')
//                     .append(num(r.avgCatD())).append(',')
//                     .append(num(r.avgCatE())).append(',')
//                     .append(num(r.overallAvg()))
//                     .append('\n');
//         }

//         byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
//         return ResponseEntity.ok()
//                 .header(HttpHeaders.CONTENT_DISPOSITION,
//                         "attachment; filename=admin_reports_semester_" + semesterId + ".csv")
//                 .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
//                 .body(bytes);
//     }

//     // ========= Export CSV (section detail) =========
//     @GetMapping(value = "/{sectionId}/export", produces = "text/csv")
//     public ResponseEntity<byte[]> exportSectionCsv(@PathVariable Long sectionId) {
//         var kind = EvaluationKind.STUDENT_FEEDBACK;

//         var section = sectionRepo.findById(sectionId).orElseThrow();
//         var qStats = answerRepo.adminQuestionStats(sectionId, kind);
//         var comments = answerRepo.adminComments(sectionId, kind);

//         StringBuilder sb = new StringBuilder();

//         // ✅ UTF-8 BOM for Excel (fix Khmer "????")
//         sb.append('\uFEFF');

//         sb.append("Course,").append(csv(section.getCourse().getCode() + " - " + section.getCourse().getName()))
//                 .append("\n");
//         sb.append("Teacher,").append(csv(section.getTeacher().getUsername())).append("\n\n");

//         sb.append("No,Question,Min,Max,Avg,N\n");
//         for (var q : qStats) {
//             sb.append(q.orderNo()).append(',')
//                     .append(csv(q.questionText())).append(',')
//                     .append(q.minScore() == null ? "" : q.minScore()).append(',')
//                     .append(q.maxScore() == null ? "" : q.maxScore()).append(',')
//                     .append(q.avgScore() == null ? "" : String.format(Locale.US, "%.2f", q.avgScore())).append(',')
//                     .append(q.n() == null ? "" : q.n())
//                     .append('\n');
//         }

//         sb.append("\nComments\n");
//         for (var c : comments) {
//             sb.append(csv(c)).append('\n');
//         }

//         byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);

//         return ResponseEntity.ok()
//                 .header(HttpHeaders.CONTENT_DISPOSITION,
//                         "attachment; filename=section_report_" + sectionId + ".csv")
//                 // ✅ ensure charset in header (helps non-Excel clients too)
//                 .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
//                 .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
//                 .body(bytes);
//     }

//     // ========= Export PDF (section detail) =========
//     public static class PdfHeaderVm {
//         private final String teacherName;
//         private final String teachingClass;
//         private final String cohort;
//         private final String promotion;
//         private final String shiftTime;
//         private final String semesterName;
//         private final String course;
//         private final String room;

//         public PdfHeaderVm(String teacherName, String teachingClass, String cohort, String promotion,
//                 String shiftTime, String semesterName, String course, String room) {
//             this.teacherName = teacherName;
//             this.teachingClass = teachingClass;
//             this.cohort = cohort;
//             this.promotion = promotion;
//             this.shiftTime = shiftTime;
//             this.semesterName = semesterName;
//             this.course = course;
//             this.room = room;
//         }

//         public String getTeacherName() {
//             return teacherName;
//         }

//         public String getTeachingClass() {
//             return teachingClass;
//         }

//         public String getCohort() {
//             return cohort;
//         }

//         public String getPromotion() {
//             return promotion;
//         }

//         public String getShiftTime() {
//             return shiftTime;
//         }

//         public String getSemesterName() {
//             return semesterName;
//         }

//         public String getCourse() {
//             return course;
//         }

//         public String getRoom() {
//             return room;
//         }
//     }

//     public static class PdfQuestionVm {
//         private final Integer orderNo;
//         private final String questionText;
//         private final Integer minScore;
//         private final Integer maxScore;
//         private final Double avgScore;
//         private final Long n;

//         public PdfQuestionVm(Integer orderNo, String questionText, Integer minScore, Integer maxScore, Double avgScore,
//                 Long n) {
//             this.orderNo = orderNo;
//             this.questionText = questionText;
//             this.minScore = minScore;
//             this.maxScore = maxScore;
//             this.avgScore = avgScore;
//             this.n = n;
//         }

//         public static PdfQuestionVm from(AdminQuestionScoreStat s) {
//             return new PdfQuestionVm(s.orderNo(), s.questionText(), s.minScore(), s.maxScore(), s.avgScore(), s.n());
//         }

//         public Integer getOrderNo() {
//             return orderNo;
//         }

//         public String getQuestionText() {
//             return questionText;
//         }

//         public Integer getMinScore() {
//             return minScore;
//         }

//         public Integer getMaxScore() {
//             return maxScore;
//         }

//         public Double getAvgScore() {
//             return avgScore;
//         }

//         public Long getN() {
//             return n;
//         }
//     }

//     public static class PdfCategoryVm {
//         private final String label; // ក, ខ, គ, ឃ, ង
//         private final int start;
//         private final int end;
//         private final String rangeText; // (Q1–Q10)
//         private final String avgText;
//         private final List<PdfQuestionVm> questions;

//         public PdfCategoryVm(String label, int start, int end, String rangeText, String avgText,
//                 List<PdfQuestionVm> questions) {
//             this.label = label;
//             this.start = start;
//             this.end = end;
//             this.rangeText = rangeText;
//             this.avgText = avgText;
//             this.questions = questions;
//         }

//         public String getLabel() {
//             return label;
//         }

//         public int getStart() {
//             return start;
//         }

//         public int getEnd() {
//             return end;
//         }

//         public String getRangeText() {
//             return rangeText;
//         }

//         public String getAvgText() {
//             return avgText;
//         }

//         public List<PdfQuestionVm> getQuestions() {
//             return questions;
//         }
//     }

//     @GetMapping(value = "/{sectionId}/export.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
//     public ResponseEntity<byte[]> exportSectionPdf(@PathVariable Long sectionId,
//             @RequestParam(defaultValue = "en") String lang) {

//         var kind = EvaluationKind.STUDENT_FEEDBACK;

//         var section = sectionRepo.findById(sectionId).orElseThrow();
//         var qStats = answerRepo.adminQuestionStats(sectionId, kind);
//         var comments = answerRepo.adminComments(sectionId, kind)
//                 .stream()
//                 .filter(c -> c != null && !c.isBlank())
//                 .map(String::trim)
//                 .distinct()
//                 .limit(20)
//                 .collect(Collectors.joining("\n• ", "• ", ""));
//         List<String> commentList = answerRepo.adminComments(sectionId, kind)
//                 .stream()
//                 .filter(c -> c != null && !c.isBlank())
//                 .map(String::trim)
//                 .distinct()
//                 .limit(30)
//                 .toList();

//         long count = submissionRepo.countByKindAndSectionId(kind, sectionId);
//         long enrolled = enrollmentRepo.countBySection_Id(sectionId);
//         Double responseRate = (enrolled == 0) ? null : (count * 100.0 / enrolled);
//         Double overall = avgRange(qStats, 1, 999);
//         long responded = count;
//         long notResponded = Math.max(0, enrolled - responded);

//         String teacherName = firstNonBlank(
//                 safeStr(() -> reflectString(section.getTeacher(), "getFullName", "getName")),
//                 safeStr(() -> section.getTeacher().getUsername()),
//                 "-");

//         String teachingClass = firstNonBlank(
//                 safeStr(() -> reflectString(section, "getTeachingClass", "getClassName", "getClassCode")),
//                 safeStr(() -> section.getSectionName()),
//                 "-");

//         String cohort = firstNonBlank(
//                 safeStr(() -> reflectString(section, "getCohort", "getCohortName", "getCohortCode")),
//                 "-");

//         String promotion = firstNonBlank(
//                 safeStr(() -> reflectString(section, "getPromotion", "getPromotionName", "getPromotionCode")),
//                 "-");

//         String shiftTime = firstNonBlank(safeStr(() -> String.valueOf(section.getShiftTime())), "-");

//         String semesterName = firstNonBlank(
//                 safeStr(() -> section.getSemester() != null ? section.getSemester().getName() : null),
//                 "-");

//         String course = firstNonBlank(
//                 safeStr(() -> section.getCourse() != null
//                         ? section.getCourse().getCode() + " - " + section.getCourse().getName()
//                         : null),
//                 "-");

//         String room = firstNonBlank(
//                 safeStr(() -> (section.getBuilding() != null ? section.getBuilding() : "-") + "-"
//                         + (section.getRoom() != null ? section.getRoom() : "-")),
//                 "-");

//         // categories (same idea as your existing code)
//         List<PdfCategoryVm> categories = List.of(
//                 buildCatVm(addKhmerBreaks("ក.ចំណេះដឹង និងជំនាញរបស់គ្រូបង្រៀន", 100), 1, 10, qStats),
//                 buildCatVm(addKhmerBreaks("ខ.ការផ្តល់ឯកសារសិក្សាដល់និស្សិត", 100), 11, 13, qStats),
//                 buildCatVm(addKhmerBreaks("គ.ការអនុវត្តតាមគោលការណ៍របស់សាកលវិទ្យាល័យ", 100), 14, 18, qStats),
//                 buildCatVm(addKhmerBreaks("ឃ.ទំនាក់ទំនងរវាងគ្រូបង្រៀនជាមួយនិស្សិត", 100), 19, 21, qStats),
//                 buildCatVm(addKhmerBreaks("ង.ក្រមសីលធម៌វិជ្ជាជីវៈ", 100), 22, 26, qStats));

//         // flatten rows for Jasper
//         List<JasperRowVm> rows = new ArrayList<>();

//         for (PdfCategoryVm cat : categories) {
//             if (cat.getQuestions() == null || cat.getQuestions().isEmpty()) {
//                 rows.add(new JasperRowVm(
//                         cat.getLabel(), cat.getRangeText(), cat.getAvgText(),
//                         null, "No questions found in this category.", "-", "-", "-", "-"));
//             } else {
//                 for (PdfQuestionVm q : cat.getQuestions()) {
//                     String nText;
//                     if (q.getN() == null) {
//                         nText = "-";
//                     } else if (enrolled <= 0) {
//                         nText = String.valueOf(q.getN());
//                     } else {
//                         double pct = (q.getN() * 100.0) / enrolled;
//                         nText = String.format(Locale.US, "%d (%.0f%%)", q.getN(), pct); // short, fits width=50
//                     }
//                     rows.add(new JasperRowVm(
//                             cat.getLabel(), cat.getRangeText(), cat.getAvgText(),
//                             q.getOrderNo(),
//                             addKhmerBreaks(q.getQuestionText(), 45),
//                             q.getMinScore() == null ? "-" : String.valueOf(q.getMinScore()),
//                             q.getMaxScore() == null ? "-" : String.valueOf(q.getMaxScore()),
//                             q.getAvgScore() == null ? "-" : String.format(Locale.US, "%.2f", q.getAvgScore()),
//                             nText));
//                 }
//             }
//         }

//         String generatedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

//         Map<String, Object> params = new HashMap<>();
//         params.put(JRParameter.REPORT_LOCALE, Locale.forLanguageTag("km"));

//         params.put("teacherName", teacherName);
//         params.put("teachingClass", teachingClass);
//         params.put("cohort", cohort);
//         params.put("promotion", promotion);
//         params.put("shiftTime", shiftTime);
//         params.put("semesterName", semesterName);
//         params.put("course", course);
//         params.put("room", room);
//         params.put("comments", comments);

//         // AI summary — use language chosen by admin (lang=km for Khmer, lang=en for English)
//         String courseName = (section.getCourse() != null)
//                 ? section.getCourse().getCode() + " - " + section.getCourse().getName()
//                 : "Unknown Course";
//         var aiSummaryDto = feedbackAiService.summarizeComments(sectionId, commentList, courseName, teacherName);
//         boolean useKhmer = "km".equalsIgnoreCase(lang);

//         if (useKhmer && aiSummaryDto != null) {
//             // Auto-translate to Khmer if not cached; translateToKhmer returns cached or calls Claude
//             var khmerDto = feedbackAiService.translateToKhmer(sectionId, aiSummaryDto);
//             if (khmerDto != null) {
//                 params.put("aiSummary", addKhmerBreaks(
//                         khmerDto.getSummary() != null ? khmerDto.getSummary() : "-", 28));
//                 params.put("aiStrengths", addKhmerBreaks(khmerDto.getStrengths() != null
//                         ? khmerDto.getStrengths().stream().map(s -> "• " + s).collect(Collectors.joining("\n"))
//                         : "-", 28));
//                 params.put("aiImprovements", addKhmerBreaks(khmerDto.getImprovements() != null
//                         ? khmerDto.getImprovements().stream().map(s -> "• " + s).collect(Collectors.joining("\n"))
//                         : "-", 28));
//                 params.put("aiRecommendation", addKhmerBreaks(
//                         khmerDto.getRecommendation() != null ? khmerDto.getRecommendation() : "-", 28));
//             } else {
//                 params.put("aiSummary", "-");
//                 params.put("aiStrengths", "-");
//                 params.put("aiImprovements", "-");
//                 params.put("aiRecommendation", "-");
//             }
//         } else if (aiSummaryDto != null) {
//             params.put("aiSummary", aiSummaryDto.getSummary());
//             params.put("aiStrengths", aiSummaryDto.getStrengths().stream()
//                     .map(s -> "• " + s).collect(Collectors.joining("\n")));
//             params.put("aiImprovements", aiSummaryDto.getImprovements().stream()
//                     .map(s -> "• " + s).collect(Collectors.joining("\n")));
//             params.put("aiRecommendation", aiSummaryDto.getRecommendation() != null ? aiSummaryDto.getRecommendation() : "-");
//         } else {
//             params.put("aiSummary", "-");
//             params.put("aiStrengths", "-");
//             params.put("aiImprovements", "-");
//             params.put("aiRecommendation", "-");
//         }

//         params.put("count", String.valueOf(count));
//         params.put("enrolled", String.valueOf(enrolled));
//         params.put("responded", String.valueOf(responded));
//         params.put("notResponded", String.valueOf(notResponded));
//         params.put("responseRateText", responseRate == null ? "-" : String.format(Locale.US, "%.2f%%", responseRate));
//         params.put("overallText", overall == null ? "-" : String.format(Locale.US, "%.2f", overall));
//         params.put("generatedAt", generatedAt);

//         // Khmer date for signature block  e.g. "រាជធានីភ្នំពេញ, ថ្ងៃទី ១៧ ខែ ០៣ ឆ្នាំ ២០២៦"
//         java.time.LocalDate today = java.time.LocalDate.now();
//         String khmerDate = "រាជធានីភ្នំពេញ, ថ្ងៃទី "
//                 + toKhmerDigits(String.format("%02d", today.getDayOfMonth()))
//                 + " ខែ "
//                 + toKhmerDigits(String.format("%02d", today.getMonthValue()))
//                 + " ឆ្នាំ "
//                 + toKhmerDigits(String.valueOf(today.getYear()));
//         params.put("khmerDate", khmerDate);

//         try {
//             String path = "classpath:reports/section_report_km.jrxml";
//             File file = ResourceUtils.getFile(path);
//             // IMPORTANT: make sure this file exists and is valid XML
//             // InputStream jrxml = resource("/reports/section_report_km.jrxml");
//             JasperReport report = JasperCompileManager.compileReport(file.getAbsolutePath());

//             JRBeanCollectionDataSource ds = new JRBeanCollectionDataSource(rows);
//             JasperPrint print = JasperFillManager.fillReport(report, params, ds);

//             byte[] pdfBytes = JasperExportManager.exportReportToPdf(print);

//             return ResponseEntity.ok()
//                     .header(HttpHeaders.CONTENT_DISPOSITION,
//                             "attachment; filename=section_report_" + section.getTeacher().getUsername() + ".pdf")
//                     .contentType(MediaType.APPLICATION_PDF)
//                     .body(pdfBytes);

//         } catch (JRException e) {
//             Throwable root = e;
//             while (root.getCause() != null)
//                 root = root.getCause();
//             throw new RuntimeException("Jasper JRXML load/compile failed. Root cause: " + root.getMessage(), e);
//         } catch (Exception e) {
//             throw new RuntimeException("PDF export failed (Jasper): " + e.getMessage(), e);
//         }
//     }

//     /** Row VM used by Jasper template */
//     public static class JasperRowVm {
//         private final String categoryLabel;
//         private final String rangeText;
//         private final String categoryAvgText;

//         private final Integer orderNo;
//         private final String questionText;
//         private final String minText;
//         private final String maxText;
//         private final String avgText;
//         private final String nText;

//         public JasperRowVm(String categoryLabel, String rangeText, String categoryAvgText,
//                 Integer orderNo, String questionText,
//                 String minText, String maxText, String avgText, String nText) {
//             this.categoryLabel = categoryLabel;
//             this.rangeText = rangeText;
//             this.categoryAvgText = categoryAvgText;
//             this.orderNo = orderNo;
//             this.questionText = questionText;
//             this.minText = minText;
//             this.maxText = maxText;
//             this.avgText = avgText;
//             this.nText = nText;
//         }

//         public String getCategoryLabel() {
//             return categoryLabel;
//         }

//         public String getRangeText() {
//             return rangeText;
//         }

//         public String getCategoryAvgText() {
//             return categoryAvgText;
//         }

//         public Integer getOrderNo() {
//             return orderNo;
//         }

//         public String getQuestionText() {
//             return questionText;
//         }

//         public String getMinText() {
//             return minText;
//         }

//         public String getMaxText() {
//             return maxText;
//         }

//         public String getAvgText() {
//             return avgText;
//         }

//         public String getNText() {
//             return nText;
//         }
//     }

//     private static PdfCategoryVm buildCatVm(String label, int start, int end, List<AdminQuestionScoreStat> all) {
//         List<PdfQuestionVm> qs = all.stream()
//                 .filter(q -> q.orderNo() != null && q.orderNo() >= start && q.orderNo() <= end)
//                 .sorted(Comparator.comparing(AdminQuestionScoreStat::orderNo))
//                 .map(PdfQuestionVm::from)
//                 .toList();

//         Double avg = avgRange(all, start, end);
//         String avgText = (avg == null) ? "-" : String.format(Locale.US, "%.2f", avg);
//         String rangeText = "(Q" + start + "–Q" + end + ")";

//         return new PdfCategoryVm(label, start, end, rangeText, avgText, qs);
//     }

//     private static String reflectString(Object target, String... methodNames) {
//         if (target == null)
//             return null;
//         for (String m : methodNames) {
//             try {
//                 var method = target.getClass().getMethod(m);
//                 Object val = method.invoke(target);
//                 if (val != null) {
//                     String s = val.toString().trim();
//                     if (!s.isEmpty())
//                         return s;
//                 }
//             } catch (Exception ignored) {
//             }
//         }
//         return null;
//     }

//     private static String safeStr(Supplier<String> supplier) {
//         try {
//             return supplier.get();
//         } catch (Exception e) {
//             return null;
//         }
//     }

//     private static String firstNonBlank(String... xs) {
//         for (String x : xs) {
//             if (x != null && !x.trim().isEmpty())
//                 return x.trim();
//         }
//         return null;
//     }

//     /**
//      * Inserts zero-width spaces (U+200B) before Khmer syllable-start characters
//      * once {@code chunkSize} Khmer characters accumulate, so JasperReports can
//      * line-wrap without breaking mid-syllable.
//      *
//      * Safe break points: base consonants (U+1780–U+17A2) and independent vowels
//      * (U+17A3–U+17B3) only.  Excluded from break points:
//      *   - U+17B4/U+17B5 (inherent vowels – invisible combining characters)
//      *   - U+17B6–U+17FF (dependent vowels, diacritics, coeng, punctuation)
//      *   - Any consonant immediately after coeng U+17D2 (subscript cluster member)
//      */
//     private static String addKhmerBreaks(String text, int chunkSize) {
//         if (text == null) return null;
//         StringBuilder sb = new StringBuilder(text.length() + 32);
//         int run = 0;
//         char prev = 0;
//         for (int i = 0; i < text.length(); i++) {
//             char c = text.charAt(i);
//             boolean isKhmer = c >= '\u1780' && c <= '\u17FF';
//             // U+1780–U+17B3: base consonants + independent vowels (true syllable starters).
//             // U+17B4 and U+17B5 are inherent (combining) vowels — excluded.
//             boolean isSyllableStart = c >= '\u1780' && c <= '\u17B3' && prev != '\u17D2';

//             if (run >= chunkSize && isSyllableStart) {
//                 sb.append('\n');
//                 run = 0;
//             }

//             sb.append(c);
//             prev = c;

//             if (isKhmer) {
//                 run++;
//             } else if (c == ' ' || c == '\n') {
//                 run = 0;
//             }
//         }
//         return sb.toString();
//     }

//     private static String toKhmerDigits(String s) {
//         StringBuilder sb = new StringBuilder(s.length());
//         for (char c : s.toCharArray()) {
//             sb.append(c >= '0' && c <= '9' ? (char) (c - '0' + '\u17E0') : c);
//         }
//         return sb.toString();
//     }

//     // ========= Helpers =========
//     private static Double avgRange(List<AdminQuestionScoreStat> stats, int start,
//             int end) {
//         double sum = 0.0;
//         int n = 0;
//         for (var s : stats) {
//             if (s.orderNo() == null || s.avgScore() == null)
//                 continue;
//             if (s.orderNo() >= start && s.orderNo() <= end) {
//                 sum += s.avgScore();
//                 n++;
//             }
//         }
//         return (n == 0) ? null : (sum / n);
//     }

//     private static String csv(String s) {
//         if (s == null)
//             return "\"\"";
//         String x = s.replace("\"", "\"\"");
//         return "\"" + x + "\"";
//     }

//     private static String num(Double d) {
//         return d == null ? "" : String.format(Locale.US, "%.2f", d);
//     }

//     // Build per-teacher weighted averages (weight = section responses)
//     private static List<AdminTeacherSummaryRow> buildTeacherSummaries(List<AdminSectionReportRow> rows,
//             Map<Long, Long> enrolledMap) {
//         class Agg {
//             long responses = 0;
//             long enrolled = 0;

//             double wA = 0, wB = 0, wC = 0, wD = 0, wE = 0, wO = 0;
//             double sA = 0, sB = 0, sC = 0, sD = 0, sE = 0, sO = 0;
//         }

//         Map<String, Agg> map = new HashMap<>();

//         for (var r : rows) {
//             String teacher = r.teacherUsername();
//             Agg a = map.computeIfAbsent(teacher, k -> new Agg());

//             long resp = r.responses() == null ? 0L : r.responses();
//             long enrolled = enrolledMap.getOrDefault(r.sectionId(), 0L);

//             a.responses += resp;
//             a.enrolled += enrolled;

//             if (resp > 0) {
//                 if (r.avgCatA() != null) {
//                     a.sA += r.avgCatA() * resp;
//                     a.wA += resp;
//                 }
//                 if (r.avgCatB() != null) {
//                     a.sB += r.avgCatB() * resp;
//                     a.wB += resp;
//                 }
//                 if (r.avgCatC() != null) {
//                     a.sC += r.avgCatC() * resp;
//                     a.wC += resp;
//                 }
//                 if (r.avgCatD() != null) {
//                     a.sD += r.avgCatD() * resp;
//                     a.wD += resp;
//                 }
//                 if (r.avgCatE() != null) {
//                     a.sE += r.avgCatE() * resp;
//                     a.wE += resp;
//                 }
//                 if (r.overallAvg() != null) {
//                     a.sO += r.overallAvg() * resp;
//                     a.wO += resp;
//                 }
//             }
//         }

//         List<AdminTeacherSummaryRow> out = new ArrayList<>();
//         for (var e : map.entrySet()) {
//             Agg a = e.getValue();
//             if (a.responses <= 0)
//                 continue;

//             Double rate = (a.enrolled == 0) ? null : (a.responses * 100.0 / a.enrolled);

//             out.add(new AdminTeacherSummaryRow(
//                     e.getKey(),
//                     a.responses,
//                     a.enrolled,
//                     rate,
//                     a.wA == 0 ? null : a.sA / a.wA,
//                     a.wB == 0 ? null : a.sB / a.wB,
//                     a.wC == 0 ? null : a.sC / a.wC,
//                     a.wD == 0 ? null : a.sD / a.wD,
//                     a.wE == 0 ? null : a.sE / a.wE,
//                     a.wO == 0 ? null : a.sO / a.wO));
//         }
//         return out;
//     }

//     private static <T> List<T> topBy(List<T> list, Function<T, Double> metric, int n) {
//         return list.stream()
//                 .filter(x -> metric.apply(x) != null)
//                 .sorted(Comparator.comparing(metric).reversed())
//                 .limit(n)
//                 .toList();
//     }

//     private static <T> List<T> bottomBy(List<T> list, Function<T, Double> metric, int n) {
//         return list.stream()
//                 .filter(x -> metric.apply(x) != null)
//                 .sorted(Comparator.comparing(metric))
//                 .limit(n)
//                 .toList();
//     }

//     @GetMapping("/section/{sectionId:\\d+}/responses")
//     public String sectionResponses(@PathVariable Long sectionId, Model model) {

//         var section = sectionRepo.findById(sectionId).orElseThrow();

//         var statuses = enrollmentRepo.adminStudentFeedbackStatus(
//                 sectionId,
//                 EvaluationKind.STUDENT_FEEDBACK);

//         long total = statuses.size();
//         long submitted = statuses.stream()
//                 .filter(s -> s.getSubmittedAt() != null)
//                 .count();

//         long pending = total - submitted;
//         double rate = (total == 0) ? 0 : (submitted * 100.0 / total);
//         model.addAttribute("rate", rate);

//         model.addAttribute("section", section);
//         model.addAttribute("statuses", statuses);

//         model.addAttribute("total", total);
//         model.addAttribute("submitted", submitted);
//         model.addAttribute("pending", pending);

//         return "admin/section_responses";
//     }

//     @GetMapping(value = "/section/{sectionId:\\d+}/responses/export", produces = "text/csv")
//     public ResponseEntity<byte[]> exportStudentFeedbackStatus(@PathVariable Long sectionId) {

//         var section = sectionRepo.findById(sectionId).orElseThrow();

//         var statuses = enrollmentRepo.adminStudentFeedbackStatus(
//                 sectionId,
//                 EvaluationKind.STUDENT_FEEDBACK);

//         StringBuilder sb = new StringBuilder();

//         // UTF-8 BOM for Khmer
//         sb.append('\uFEFF');

//         sb.append("StudentID,Username,FullName,Email,Status,SubmittedAt\n");

//         for (var s : statuses) {

//             String status = s.getSubmittedAt() != null ? "Submitted" : "Not Submitted";

//             sb.append(s.getStudentId()).append(',')
//                     .append(csv(s.getStudentUsername())).append(',')
//                     .append(csv(s.getFullName())).append(',')
//                     .append(csv(s.getEmail())).append(',')
//                     .append(status).append(',')
//                     .append(s.getSubmittedAt() == null ? "" : s.getSubmittedAt())
//                     .append("\n");
//         }

//         byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);

//         return ResponseEntity.ok()
//                 .header(HttpHeaders.CONTENT_DISPOSITION,
//                         "attachment; filename=student_feedback_status_" + sectionId + ".csv")
//                 .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
//                 .body(bytes);
//     }

//     @GetMapping(value = "/section/{sectionId:\\d+}/responses/export-pending", produces = "text/csv")
//     public ResponseEntity<byte[]> exportPendingOnly(@PathVariable Long sectionId) {

//         var kind = EvaluationKind.STUDENT_FEEDBACK;
//         var section = sectionRepo.findById(sectionId).orElseThrow();
//         // System.out.println("Group Num : " + section.getGroupNo());
//         // 1) All enrolled students in this section
//         var enrollments = enrollmentRepo.findBySection_Id(sectionId);

//         // 2) Submissions for this section/kind -> map by studentId
//         var submissions = submissionRepo.findBySection_IdAndKind(sectionId, kind)
//                 .stream()
//                 .collect(Collectors.toMap(
//                         s -> s.getSubmittedBy().getId(),
//                         s -> s));

//         StringBuilder sb = new StringBuilder();
//         sb.append('\uFEFF'); // ✅ UTF-8 BOM for Khmer Excel

//         sb.append("studentLogin,fullName,cohortNo,groupNo,className,shiftTime,email,phone,status\n");

//         // Section-level info (same for all students)
//         String cohortNo = (section.getCohort() != null) ? String.valueOf(section.getCohort().getCohortNo()) : "";
//         // String groupNo = (section.getGroupNo() != null) ?
//         // String.valueOf(section.getGroupNo()) : "";
//         String className = section.getSectionName() != null ? section.getSectionName() : "";
//         String shiftTime = section.getShiftTime() != null ? section.getShiftTime().toString() : "";
//         String groupNo = "";
//         String fullName = "";

//         for (var e : enrollments) {
//             var student = e.getStudent();
//             if (student == null)
//                 continue;

//             boolean submitted = submissions.containsKey(student.getId());
//             if (submitted)
//                 continue; // ✅ pending only
//             var registry = studentRegistryRepo.findByStudentLogin(student.getUsername());

//             if (registry.isPresent()) {
//                 groupNo = String.valueOf(registry.get().getGroupNo());
//                 fullName = registry.get().getFullName();
//             }
//             sb.append(csv(student.getUsername())).append(',')
//                     .append(csv(fullName)).append(',')
//                     .append(csv(cohortNo)).append(',')
//                     .append(csv(groupNo)).append(',')
//                     .append(csv(className)).append(',')
//                     .append(csv(shiftTime)).append(',')
//                     .append(csv(student.getEmail())).append(',')
//                     .append(csv(student.getPhone())).append(',')
//                     .append("Not Submitted")
//                     .append('\n');
//         }

//         byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);

//         return ResponseEntity.ok()
//                 .header(HttpHeaders.CONTENT_DISPOSITION,
//                         "attachment; filename=pending_students_section_" + sectionId + ".csv")
//                 .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
//                 .body(bytes);
//     }

//     @GetMapping(value = "/section/{sectionId:\\d+}/responses/export-all", produces = "text/csv")
//     public ResponseEntity<byte[]> exportAllStudentsFeedbackStatus(@PathVariable Long sectionId) {

//         var kind = EvaluationKind.STUDENT_FEEDBACK;
//         var section = sectionRepo.findById(sectionId).orElseThrow();

//         var enrollments = enrollmentRepo.findBySection_Id(sectionId);

//         var submissions = submissionRepo
//                 .findBySection_IdAndKind(sectionId, EvaluationKind.STUDENT_FEEDBACK)
//                 .stream()
//                 .collect(Collectors.toMap(
//                         s -> s.getSubmittedBy().getId(),
//                         s -> s));

//         StringBuilder sb = new StringBuilder();
//         sb.append('\uFEFF'); // ✅ Khmer Excel
//         sb.append("studentLogin,fullName,cohortNo,groupNo,className,shiftTime,email,phone,status,submittedAt\n");

//         String cohortNo = (section.getCohort() != null) ? String.valueOf(section.getCohort().getCohortNo()) : "";
//         String groupNo = (section.getGroupNo() != null) ? String.valueOf(section.getGroupNo()) : "";
//         String className = section.getSectionName() != null ? section.getSectionName() : "";
//         String shiftTime = section.getShiftTime() != null ? section.getShiftTime().toString() : "";

//         for (var e : enrollments) {
//             var student = e.getStudent();
//             if (student == null)
//                 continue;

//             var sub = submissions.get(student.getId());
//             boolean submitted = (sub != null);

//             sb.append(csv(student.getUsername())).append(',')
//                     .append(csv(student.getFullName())).append(',')
//                     .append(csv(cohortNo)).append(',')
//                     .append(csv(groupNo)).append(',')
//                     .append(csv(className)).append(',')
//                     .append(csv(shiftTime)).append(',')
//                     .append(csv(student.getEmail())).append(',')
//                     .append(csv(student.getPhone())).append(',')
//                     .append(submitted ? "Submitted" : "Not Submitted").append(',')
//                     .append(csv(submitted ? String.valueOf(sub.getSubmittedAt()) : ""))
//                     .append('\n');
//         }

//         byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);

//         return ResponseEntity.ok()
//                 .header(HttpHeaders.CONTENT_DISPOSITION,
//                         "attachment; filename=all_students_feedback_status_section_" + sectionId + ".csv")
//                 .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
//                 .body(bytes);
//     }
//     // it is ok
//     // @GetMapping(value = "/section/{sectionId:\\d+}/responses/export-pending",
//     // produces = "text/csv")
//     // public ResponseEntity<byte[]> exportPendingOnly(@PathVariable Long sectionId)
//     // {

//     // var statuses = enrollmentRepo.adminStudentFeedbackStatus(sectionId,
//     // EvaluationKind.STUDENT_FEEDBACK);

//     // StringBuilder sb = new StringBuilder();
//     // sb.append('\uFEFF'); // UTF-8 BOM for Khmer Excel
//     // sb.append("StudentID,Username,FullName,Email,Phone,Status\n");

//     // for (var s : statuses) {
//     // if (s.getSubmittedAt() != null)
//     // continue; // pending only
//     // sb.append(s.getStudentId()).append(',')
//     // .append(csv(s.getStudentUsername())).append(',')
//     // .append(csv(s.getFullName())).append(',')
//     // .append(csv(s.getEmail())).append(',')
//     // // .append(csv(s.getPhone())) // if you store phone (recommended)
//     // .append(",Not Submitted\n");
//     // }

//     // byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);

//     // return ResponseEntity.ok()
//     // .header(HttpHeaders.CONTENT_DISPOSITION,
//     // "attachment; filename=pending_students_" + sectionId + ".csv")
//     // .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
//     // .body(bytes);
//     // }
// }
