package kh.edu.num.feedback.web.admin;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import kh.edu.num.feedback.domain.entity.EvaluationKind;
import kh.edu.num.feedback.domain.entity.Question;
import kh.edu.num.feedback.domain.entity.QuestionType;
import kh.edu.num.feedback.domain.repo.QuestionRepository;
import kh.edu.num.feedback.domain.repo.SemesterRepository;
import kh.edu.num.feedback.service.WindowService;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

@Controller
@RequestMapping("/admin/questions")
public class AdminQuestionController {

    private final QuestionRepository questionRepo;
    private final SemesterRepository semesterRepo;
    private final WindowService windowService;

    public AdminQuestionController(QuestionRepository questionRepo,
            SemesterRepository semesterRepo,
            WindowService windowService) {
        this.questionRepo = questionRepo;
        this.semesterRepo = semesterRepo;
        this.windowService = windowService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) EvaluationKind kind,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false, defaultValue = "1")  Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size,
            Model model) {

        List<Question> all;
        if (kind == null) {
            all = questionRepo.findAllByOrderByKindAscOrderNoAsc();
        } else if (active == null) {
            all = questionRepo.findByKindOrderByOrderNoAsc(kind);
        } else {
            all = questionRepo.findByKindAndActiveOrderByOrderNoAsc(kind, active);
        }

        int safeSize  = (size == null || size < 1) ? 20 : size;
        int safePage  = (page == null || page < 1) ? 1 : page;
        int totalRows = all.size();
        int totalPages = (int) Math.ceil(totalRows / (double) safeSize);
        if (totalPages > 0 && safePage > totalPages) safePage = totalPages;
        int from = (safePage - 1) * safeSize;
        int to   = Math.min(from + safeSize, totalRows);
        List<Question> questions = totalRows == 0 ? Collections.emptyList() : all.subList(from, to);

        model.addAttribute("questions", questions);
        model.addAttribute("kinds", EvaluationKind.values());
        model.addAttribute("types", QuestionType.values());
        model.addAttribute("filterKind", kind);
        model.addAttribute("filterActive", active);
        model.addAttribute("semesters", semesterRepo.findAll());
        model.addAttribute("page", safePage);
        model.addAttribute("size", safeSize);
        model.addAttribute("totalRows", totalRows);
        model.addAttribute("totalPages", totalPages);

        return "admin/questions";
    }

    @GetMapping("/new")
    public String createForm(@RequestParam(defaultValue = "STUDENT_FEEDBACK") EvaluationKind kind,
            Model model) {
        QuestionForm form = new QuestionForm();
        form.setKind(kind);
        form.setType(QuestionType.RATING);
        form.setScaleMin(1);
        form.setScaleMax(5);
        form.setActive(true);
        form.setOrderNo(questionRepo.maxOrderNoByKind(kind) + 1);

        model.addAttribute("form", form);
        model.addAttribute("kinds", EvaluationKind.values());
        model.addAttribute("types", QuestionType.values());
        return "admin/question_new";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") QuestionForm form,
            BindingResult br,
            Model model) {

        validateForm(form, br);

        if (br.hasErrors()) {
            model.addAttribute("kinds", EvaluationKind.values());
            model.addAttribute("types", QuestionType.values());
            return "admin/question_new";
        }

        Question q = new Question();
        applyForm(q, form);
        questionRepo.save(q);

        return "redirect:/admin/questions?kind=" + form.getKind();
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Question q = questionRepo.findById(id).orElseThrow();

        QuestionForm form = new QuestionForm();
        form.setKind(q.getKind());
        form.setType(q.getType());
        form.setText(q.getText());
        form.setScaleMin(q.getScaleMin());
        form.setScaleMax(q.getScaleMax());
        form.setOrderNo(q.getOrderNo());
        form.setActive(q.isActive());

        model.addAttribute("id", id);
        model.addAttribute("form", form);
        model.addAttribute("kinds", EvaluationKind.values());
        model.addAttribute("types", QuestionType.values());
        return "admin/question_edit";
    }

    @PostMapping("/{id:\\d+}")
    public String update(@PathVariable Long id,
            @Valid @ModelAttribute("form") QuestionForm form,
            BindingResult br,
            Model model) {

        validateForm(form, br);

        if (br.hasErrors()) {
            model.addAttribute("id", id);
            model.addAttribute("kinds", EvaluationKind.values());
            model.addAttribute("types", QuestionType.values());
            return "admin/question_edit";
        }

        Question q = questionRepo.findById(id).orElseThrow();
        applyForm(q, form);
        questionRepo.save(q);

        return "redirect:/admin/questions?kind=" + form.getKind();
    }

    @PostMapping("/{id}/toggle")
    public String toggle(@PathVariable Long id) {
        Question q = questionRepo.findById(id).orElseThrow();
        q.setActive(!q.isActive());
        questionRepo.save(q);
        return "redirect:/admin/questions?kind=" + q.getKind();
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        Question q = questionRepo.findById(id).orElseThrow();
        EvaluationKind kind = q.getKind();

        try {
            questionRepo.delete(q);
            return "redirect:/admin/questions?kind=" + kind + "&msg=deleted";
        } catch (DataIntegrityViolationException ex) {
            // If already used by answers, do soft delete instead
            q.setActive(false);
            questionRepo.save(q);
            return "redirect:/admin/questions?kind=" + kind + "&msg=deactivated";
        }
    }

    // ------------------------------------------------------
    // OPTIONAL: Seed student feedback questions (your Khmer list)
    // ------------------------------------------------------
    @PostMapping("/seed/student-default")
    public String seedStudentDefault() {
        if (questionRepo.countByKind(EvaluationKind.STUDENT_FEEDBACK) > 0) {
            return "redirect:/admin/questions?kind=STUDENT_FEEDBACK&msg=already_seeded";
        }

        int order = 1;

        List<String> rating = List.of(
                "គ្រូមានចំណេះដឹងច្បាស់លាស់លើមុខវិជ្ជា និងមេរៀនដែលបង្រៀន។",
                "សាស្រ្តាចារ្យណែនាំនិស្សិតអំពីប្លង់មុខវិជ្ជាលម្អិត (Course Syllabus) ដោយផ្តោតជាពិសេសលើលទ្ធផលសិក្សារំពឹងទុក សកម្មភាពបង្រៀននិងរៀន ព្រមទាំងវិធីវាយតម្លៃការសិក្សា។",
                "សាស្ត្រាចារ្យអនុវត្តការបង្រៀនតាមប្លង់មុខវិជ្ជាលម្អិតដែលបានណែនាំដល់និស្សិត។",
                "សាស្ត្រាចារ្យប្រើប្រាស់វិធីសាស្ត្រមានលក្ខណៈចម្រុះ ដោយរួមបញ្ចូលការប្រើប្រាស់បច្ចេកវិទ្យាព័ត៌មាន និងទំនាក់ទំនង (ICT)។",
                "សាស្ត្រាចារ្យអនុវត្តវិធីសាស្ត្របង្រៀនដែលផ្តល់ឱកាសឱ្យនិស្សិតធ្វើការអនុវត្តជាក់ស្តែង និងធ្វើសកម្មភាពនៅក្រៅថ្នាក់រៀន (Project-based Learning…)។",
                "ខ្លឹមសារមេរៀនត្រូវបានរៀបចំឱ្យមានភាពប្រទាក់ក្រឡា ដើម្បីឱ្យនិស្សិតងាយយល់ និងអនុវត្តបាន។",
                "សាស្រ្តាចារ្យផ្តល់ឱកាសឱ្យនិស្សិតប្រើប្រាស់ជំនាញគិតវិភាគ និងដោះស្រាយបញ្ហា។",
                "សាស្ត្រាចារ្យប្រើប្រាស់វិធីសាស្រ្តច្រើនបែបក្នុងការវាយតម្លៃការសិក្សារបស់និស្សិត។",
                "សាស្ត្រាចារ្យផ្តល់កិច្ចការក្នុងថ្នាក់ កិច្ចការផ្ទះ និងកិច្ចការស្រាវជ្រាវ (Assignment) គ្រប់គ្រាន់សម្រាប់និស្សិតពង្រីក និងអនុវត្តចំណេះដឹង។",
                "សាស្រ្តាចារ្យកែកិច្ចការនិស្សិត និងផ្តល់យោបល់ត្រឡប់ដល់និស្សិតទាន់ពេលវេលាសម្រាប់ការកែលម្អចំណុចខ្វះខាត។",
                "ឯកសារ/ស្លាយនៃមេរៀនមានមាតិការច្បាស់លាស់ និងងាយយល់។",
                "មេរៀនមានឯកសារយោងគ្រប់គ្រាន់។",
                "សៀវភៅសិក្សាគោលត្រូវបានកែលម្អ ឬធ្វើបច្ចុប្បន្នភាព។",
                "សាស្រ្តាចារ្យបានបង្រៀនទៀងទាត់តាមម៉ោងកំណត់ (៤៥ម៉ោង)។",
                "សាស្ត្រាចារ្យចូលបង្រៀនតាមម៉ោងកំណត់។",
                "សាស្ត្រាចារ្យចេញពីបង្រៀនតាមម៉ោងកំណត់។",
                "សាស្ត្រាចារ្យផ្សព្វផ្សាយដល់និស្សិតអំពីចក្ខុវិស័យ បេសកកម្ម ទស្សនអប់រំរបស់សាកលវិទ្យាល័យ បទបញ្ជាផ្ទៃក្នុងសម្រាប់និស្សិត និងព័ត៌មានទូទៅពាក់ព័ន្ធនឹងការសិក្សា។",
                "សាស្ត្រាចារ្យតាមដានវត្ថមាន និងវឌ្ឍនភាពនៃការសិក្សារបស់និស្សិតជាប្រចាំ។",
                "សាស្ត្រាចារ្យផ្តល់ឱកាសឱ្យនិស្សិតគ្រប់រូបបានចូលរួមបញ្ចេញមតិយោបល់ ឬលើកសំណូមពរនានាពាក់ព័ន្ធនឹងការសិក្សា។",
                "សាស្ត្រាចារ្យផ្តល់សេវាប្រឹក្សាដល់និស្សិតដែលមានការលំបាកក្នុងការសិក្សា និងការស្រាវជ្រាវពាក់ព័ន្ធនឹងវិស័យឯកទេស និងមុខវិជ្ជាដែលខ្លួនបង្រៀន។",
                "សាស្ត្រាចារ្យឆ្លើយតប និងទំនាក់ទំនងទាន់ពេលវេលា ប្រកបដោយភាពទទួលខុសត្រូវ ចំពោះបញ្ហា និងសំណូមពររបស់និស្សិតពាក់ព័ន្ធទៅនឹងការរៀន និងបង្រៀន។",
                "សាស្ត្រាចារ្យបង្ហាញភាពជាអ្នកដឹកនាំ និងជាគំរូល្អសម្រាប់និស្សិត។",
                "សាស្ត្រាចារ្យប្រកាន់ខ្ជាប់នូវកាយវិការ និងពាក្យពេចន៍ថ្លៃថ្នូរ ទៅកាន់និស្សិតគ្រប់រូប។",
                "សាស្ត្រាចារ្យយកចិត្តទុកដាក់ចំពោះនិស្សិតគ្រប់រូប និងលើកទឹកចិត្តនិស្សិតឱ្យខិតខំសិក្សា។",
                "សាស្ត្រាចារ្យប្រកាន់ភាពយុត្តិធម៌ក្នុងការវាយតម្លៃការសិក្សារបស់និស្សិត។",
                "សាស្ត្រាចារ្យស្លៀកពាក់សមរម្យ និងមានអនាម័យល្អ ទាំងការបង្រៀនផ្ទាល់ និងតាមប្រព័ន្ធអនឡាញ។");

        for (String text : rating) {
            Question q = new Question();
            q.setKind(EvaluationKind.STUDENT_FEEDBACK);
            q.setType(QuestionType.RATING);
            q.setText(text);
            q.setScaleMin(1);
            q.setScaleMax(5);
            q.setOrderNo(order++);
            q.setActive(true);
            questionRepo.save(q);
        }

        Question comment = new Question();
        comment.setKind(EvaluationKind.STUDENT_FEEDBACK);
        comment.setType(QuestionType.TEXT);
        comment.setText("មតិយោបល់បន្ថែមដើម្បីកែលម្អការបង្រៀនរបស់សាស្រ្តាចារ្យ");
        comment.setOrderNo(order);
        comment.setActive(true);
        questionRepo.save(comment);

        return "redirect:/admin/questions?kind=STUDENT_FEEDBACK&msg=seeded";
    }

    // ----------------- helpers -----------------
    private void applyForm(Question q, QuestionForm form) {
        q.setKind(form.getKind());
        q.setType(form.getType());
        q.setText(form.getText() != null ? form.getText().trim() : null);
        q.setOrderNo(form.getOrderNo());
        q.setActive(form.isActive());

        if (form.getType() == QuestionType.RATING) {
            q.setScaleMin(form.getScaleMin());
            q.setScaleMax(form.getScaleMax());
        } else {
            q.setScaleMin(null);
            q.setScaleMax(null);
        }
    }

    private void validateForm(QuestionForm form, BindingResult br) {
        if (form.getType() == QuestionType.RATING) {
            if (form.getScaleMin() == null)
                br.rejectValue("scaleMin", "required", "Scale min is required for rating");
            if (form.getScaleMax() == null)
                br.rejectValue("scaleMax", "required", "Scale max is required for rating");
            if (form.getScaleMin() != null && form.getScaleMax() != null && form.getScaleMin() > form.getScaleMax()) {
                br.rejectValue("scaleMax", "invalid", "Scale max must be >= scale min");
            }
        }
    }

    @PostMapping("/{id}/move-up")
    public String moveUp(@PathVariable Long id) {
        Question current = questionRepo.findById(id).orElseThrow();
        EvaluationKind kind = current.getKind();

        Integer currentOrder = current.getOrderNo();
        if (currentOrder == null) {
            // if null, put it at the end
            current.setOrderNo(questionRepo.maxOrderNoByKind(kind) + 1);
            questionRepo.save(current);
            return "redirect:/admin/questions?kind=" + kind;
        }

        var prevOpt = questionRepo.findFirstByKindAndOrderNoLessThanOrderByOrderNoDesc(kind, currentOrder);
        if (prevOpt.isEmpty()) {
            return "redirect:/admin/questions?kind=" + kind;
        }

        Question prev = prevOpt.get();

        // swap orderNo
        int tmp = prev.getOrderNo();
        prev.setOrderNo(current.getOrderNo());
        current.setOrderNo(tmp);

        questionRepo.save(prev);
        questionRepo.save(current);

        return "redirect:/admin/questions?kind=" + kind;
    }

    @PostMapping("/{id}/move-down")
    public String moveDown(@PathVariable Long id) {
        Question current = questionRepo.findById(id).orElseThrow();
        EvaluationKind kind = current.getKind();

        Integer currentOrder = current.getOrderNo();
        if (currentOrder == null) {
            current.setOrderNo(questionRepo.maxOrderNoByKind(kind) + 1);
            questionRepo.save(current);
            return "redirect:/admin/questions?kind=" + kind;
        }

        var nextOpt = questionRepo.findFirstByKindAndOrderNoGreaterThanOrderByOrderNoAsc(kind, currentOrder);
        if (nextOpt.isEmpty()) {
            return "redirect:/admin/questions?kind=" + kind;
        }

        Question next = nextOpt.get();

        // swap orderNo
        int tmp = next.getOrderNo();
        next.setOrderNo(current.getOrderNo());
        current.setOrderNo(tmp);

        questionRepo.save(next);
        questionRepo.save(current);

        return "redirect:/admin/questions?kind=" + kind;
    }

    @PostMapping("/normalize")
    public String normalize(@RequestParam EvaluationKind kind) {
        var list = questionRepo.findByKindOrderByOrderNoAsc(kind);
        int i = 1;
        for (Question q : list) {
            q.setOrderNo(i++);
        }
        questionRepo.saveAll(list);
        return "redirect:/admin/questions?kind=" + kind + "&msg=normalized";
    }

    @PostMapping("/publish")
    public String publishAll(@RequestParam EvaluationKind kind) {
        questionRepo.updateActiveByKind(kind, true);
        return "redirect:/admin/questions?kind=" + kind + "&msg=published";
    }

    @PostMapping("/unpublish")
    public String unpublishAll(@RequestParam EvaluationKind kind) {
        questionRepo.updateActiveByKind(kind, false);
        return "redirect:/admin/questions?kind=" + kind + "&msg=unpublished";
    }

    @PostMapping("/window/open")
    public String openWindow(@RequestParam Long semesterId,
            @RequestParam EvaluationKind kind,
            @RequestParam(defaultValue = "7") int days) {
        windowService.openNowForDays(semesterId, kind, days);
        return "redirect:/admin/questions?kind=" + kind + "&msg=window_opened";
    }

    @PostMapping("/window/close")
    public String closeWindow(@RequestParam Long semesterId,
            @RequestParam EvaluationKind kind) {
        windowService.closeNow(semesterId, kind);
        return "redirect:/admin/questions?kind=" + kind + "&msg=window_closed";
    }

    // ------------------------------------------------------
    // Export / Import (CSV)
    // ------------------------------------------------------

    @GetMapping("/export")
    public void exportCsv(
            @RequestParam(defaultValue = "STUDENT_FEEDBACK") EvaluationKind kind,
            HttpServletResponse response) throws IOException {

        var questions = questionRepo.findByKindOrderByOrderNoAsc(kind);

        response.setCharacterEncoding("UTF-8");
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition",
                "attachment; filename=questions_" + kind.name().toLowerCase() + ".csv");

        try (PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8))) {

            // ✅ UTF-8 BOM for Excel
            writer.write('\uFEFF');

            writer.println("id,kind,type,text,orderNo,active,scaleMin,scaleMax");

            for (var q : questions) {
                String text = q.getText() == null ? "" : q.getText();
                text = text.replace("\"", "\"\""); // escape quotes for CSV

                writer.printf("%s,%s,%s,\"%s\",%d,%s,%d,%d%n",
                        q.getId() == null ? "" : q.getId(),
                        q.getKind() == null ? kind.name() : q.getKind().name(),
                        q.getType() == null ? "" : q.getType().name(),
                        text,
                        q.getOrderNo(),
                        q.isActive(),
                        q.getScaleMin(),
                        q.getScaleMax());
            }
            writer.flush();
        }
    }
    // ------------------------------------------------------
    // Export / Import (excel)
    // ------------------------------------------------------

    @GetMapping("/export-excel")
    public void exportExcel(
            @RequestParam(defaultValue = "STUDENT_FEEDBACK") EvaluationKind kind,
            HttpServletResponse response) throws IOException {

        List<Question> questions = questionRepo.findByKindOrderByOrderNoAsc(kind);

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Questions");

        // Header row
        Row header = sheet.createRow(0);
        String[] columns = { "ID", "Kind", "Type", "Question Text",
                "Order", "Active", "Scale Min", "Scale Max" };

        for (int i = 0; i < columns.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(columns[i]);
        }

        // Data rows
        int rowIdx = 1;
        for (Question q : questions) {
            Row row = sheet.createRow(rowIdx++);

            setLongCell(row, 0, q.getId());
            setStringCell(row, 1, q.getKind() != null ? q.getKind().name() : kind.name());
            setStringCell(row, 2, q.getType() != null ? q.getType().name() : "");
            setStringCell(row, 3, q.getText());

            setIntCell(row, 4, q.getOrderNo());
            setBoolCell(row, 5, q.isActive());

            // ✅ Prevent NPE when scaleMin/scaleMax are null (TEXT questions etc.)
            setIntCell(row, 6, q.getScaleMin());
            setIntCell(row, 7, q.getScaleMax());
        }

        // ✅ Adjust column width
        sheet.setColumnWidth(0, 4000); // ID
        sheet.setColumnWidth(1, 6000); // Kind
        sheet.setColumnWidth(2, 6000); // Type
        sheet.setColumnWidth(3, 20000); // Question Text (wide for Khmer)
        sheet.setColumnWidth(4, 4000);
        sheet.setColumnWidth(5, 4000);
        sheet.setColumnWidth(6, 4000);
        sheet.setColumnWidth(7, 4000);

        response.setContentType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment; filename=questions_" +
                        kind.name().toLowerCase() + ".xlsx");

        workbook.write(response.getOutputStream());
        workbook.close();
    }

    @PostMapping("/delete-all")
    public String deleteAll(@RequestParam EvaluationKind kind) {
        questionRepo.deleteAllByKind(kind);
        return "redirect:/admin/questions?kind=" + kind + "&msg=deleted_all";
    }

    private String getCellString(Cell cell) {
        if (cell == null)
            return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> null;
        };
    }

    private Integer getCellInteger(Cell cell) {
        if (cell == null)
            return null;
        return switch (cell.getCellType()) {
            case NUMERIC -> (int) cell.getNumericCellValue();
            case STRING -> {
                String s = cell.getStringCellValue();
                yield (s == null || s.isBlank()) ? null : Integer.valueOf(s.trim());
            }
            default -> null;
        };
    }

    private Boolean getCellBoolean(Cell cell) {
        if (cell == null)
            return null;
        return switch (cell.getCellType()) {
            case BOOLEAN -> cell.getBooleanCellValue();
            case STRING -> {
                String s = cell.getStringCellValue();
                yield (s == null || s.isBlank()) ? null : Boolean.valueOf(s.trim());
            }
            default -> null;
        };
    }

    /**
     * Import CSV file into questions.
     *
     * Supported columns (header required):
     * kind,orderNo,type,active,scaleMin,scaleMax,text
     * Optional:
     * id (if present and exists -> update)
     *
     * mode:
     * append (default) -> upsert rows, keep existing others
     * replace -> delete all questions of the selected kind first, then import
     *
     * If a CSV row has empty "kind", it will use the selectedKind parameter.
     */

    @GetMapping(value = "/export-template", produces = "text/csv")
    @ResponseBody
    public org.springframework.http.ResponseEntity<byte[]> exportTemplate() {
        String header = "id,kind,orderNo,type,active,scaleMin,scaleMax,text\n"
                + ",STUDENT_FEEDBACK,1,RATING,true,1,5,\"Example rating question\"\n"
                + ",STUDENT_FEEDBACK,2,TEXT,true,,, \"Example text question\"\n";

        byte[] bytes = header.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"questions_template.csv\"")
                .contentType(org.springframework.http.MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(bytes);
    }

    @PostMapping("/import")
    public String importCsv(
            @RequestParam EvaluationKind kind,
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "append") String mode,
            RedirectAttributes ra) {

        if (file.isEmpty()) {
            ra.addFlashAttribute("error", "CSV file is empty.");
            return "redirect:/admin/questions?kind=" + kind;
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            if ("replace".equalsIgnoreCase(mode)) {
                questionRepo.deleteAllByKind(kind);
            }

            String line;
            reader.readLine(); // skip header
            String header = reader.readLine(); // header (may contain BOM)
            if (header != null)
                header = header.replace("\uFEFF", ""); // remove BOM if exists
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");

                Question q = new Question();
                q.setKind(kind); // 🔒 FORCE KIND HERE
                q.setType(QuestionType.valueOf(parts[2]));
                q.setText(parts[3].replace("\"", ""));
                q.setOrderNo(Integer.parseInt(parts[4]));
                q.setActive(Boolean.parseBoolean(parts[5]));
                q.setScaleMin(Integer.parseInt(parts[6]));
                q.setScaleMax(Integer.parseInt(parts[7]));

                questionRepo.save(q);
            }

            ra.addFlashAttribute("msg", "Import successful for " + kind);

        } catch (Exception e) {
            ra.addFlashAttribute("error", "Import failed: " + e.getMessage());
        }

        return "redirect:/admin/questions?kind=" + kind;
    }

    @PostMapping("/import-excel")
    public String importExcel(
            @RequestParam EvaluationKind kind,
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "append") String mode,
            RedirectAttributes ra) {

        if (file.isEmpty()) {
            ra.addFlashAttribute("error", "Excel file is empty.");
            return "redirect:/admin/questions?kind=" + kind;
        }

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);

            if ("replace".equalsIgnoreCase(mode)) {
                questionRepo.deleteAllByKind(kind);
            }

            int imported = 0;

            for (int i = 1; i <= sheet.getLastRowNum(); i++) { // row 0 = header
                Row row = sheet.getRow(i);
                if (row == null)
                    continue;

                String typeStr = getCellString(row.getCell(2)); // Type
                String text = getCellString(row.getCell(3)); // Question Text
                Integer orderNo = getCellInteger(row.getCell(4)); // Order
                Boolean active = getCellBoolean(row.getCell(5)); // Active
                Integer scaleMin = getCellInteger(row.getCell(6));
                Integer scaleMax = getCellInteger(row.getCell(7));

                if (typeStr == null || typeStr.isBlank() || text == null || text.isBlank()) {
                    continue;
                }

                Question q = new Question();
                q.setKind(kind);
                q.setType(QuestionType.valueOf(typeStr.trim().toUpperCase()));
                q.setText(text.trim());
                q.setOrderNo(orderNo != null ? orderNo : (questionRepo.maxOrderNoByKind(kind) + 1));
                q.setActive(active != null ? active : true);

                if (q.getType() == QuestionType.RATING) {
                    q.setScaleMin(scaleMin != null ? scaleMin : 1);
                    q.setScaleMax(scaleMax != null ? scaleMax : 5);
                } else {
                    q.setScaleMin(null);
                    q.setScaleMax(null);
                }

                questionRepo.save(q);
                imported++;
            }

            return "redirect:/admin/questions?kind=" + kind + "&msg=import_ok&imported=" + imported
                    + "&updated=0&skipped=0";

        } catch (Exception e) {
            ra.addFlashAttribute("error", "Excel import failed: " + e.getMessage());
            return "redirect:/admin/questions?kind=" + kind;
        }
    }
    // ----------------- CSV helpers -----------------

    private String toCsvRow(Question q) {
        return String.join(",",
                String.valueOf(q.getId()),
                escapeCsv(q.getKind() != null ? q.getKind().name() : ""),
                q.getOrderNo() != null ? q.getOrderNo().toString() : "",
                escapeCsv(q.getType() != null ? q.getType().name() : ""),
                String.valueOf(q.isActive()),
                q.getScaleMin() != null ? q.getScaleMin().toString() : "",
                q.getScaleMax() != null ? q.getScaleMax().toString() : "",
                escapeCsv(q.getText() != null ? q.getText() : ""));
    }

    private String escapeCsv(String s) {
        if (s == null)
            return "";
        boolean mustQuote = s.contains(",") || s.contains(" ") || s.contains(" ") || s.contains("\"");
        String out = s.replace("\"", "\"\"");
        return mustQuote ? ("\"" + out + "\"") : out;
    }

    private List<String> parseCsvLine(String line) {
        List<String> out = new java.util.ArrayList<>();
        if (line == null)
            return out;

        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++; // skip escaped quote
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    out.add(cur.toString());
                    cur.setLength(0);
                } else {
                    cur.append(c);
                }
            }
        }
        out.add(cur.toString());
        return out;
    }

    private String getCsv(List<String> cols, java.util.Map<String, Integer> idx, String key) {
        Integer i = idx.get(key);
        if (i == null)
            return null;
        if (i < 0 || i >= cols.size())
            return null;
        return cols.get(i);
    }

    private Integer parseIntOrNull(String s) {
        if (s == null || s.isBlank())
            return null;
        return Integer.valueOf(s.trim());
    }

    private Boolean parseBoolOrNull(String s) {
        if (s == null || s.isBlank())
            return null;
        return Boolean.valueOf(s.trim());
    }

    // ----------------- Excel helpers (null-safe) -----------------
    private static void setLongCell(Row row, int col, Long v) {
        Cell c = row.createCell(col);
        if (v != null)
            c.setCellValue(v);
        else
            c.setBlank();
    }

    private static void setIntCell(Row row, int col, Integer v) {
        Cell c = row.createCell(col);
        if (v != null)
            c.setCellValue(v);
        else
            c.setBlank();
    }

    private static void setBoolCell(Row row, int col, boolean v) {
        // primitive boolean is never null
        row.createCell(col).setCellValue(v);
    }

    private static void setStringCell(Row row, int col, String v) {
        row.createCell(col).setCellValue(v == null ? "" : v);
    }

}

// package kh.edu.num.feedback.web.admin;

// import jakarta.validation.Valid;
// import kh.edu.num.feedback.domain.entity.EvaluationKind;
// import kh.edu.num.feedback.domain.entity.Question;
// import kh.edu.num.feedback.domain.entity.QuestionType;
// import kh.edu.num.feedback.domain.repo.QuestionRepository;
// import kh.edu.num.feedback.domain.repo.SemesterRepository;
// import kh.edu.num.feedback.service.WindowService;

// import org.springframework.dao.DataIntegrityViolationException;
// import org.springframework.stereotype.Controller;
// import org.springframework.ui.Model;
// import org.springframework.validation.BindingResult;
// import org.springframework.web.bind.annotation.*;

// import java.util.List;

// @Controller
// @RequestMapping("/admin/questions")
// public class AdminQuestionController {

// private final QuestionRepository questionRepo;
// private final SemesterRepository semesterRepo;
// private final WindowService windowService;

// public AdminQuestionController(QuestionRepository questionRepo,
// SemesterRepository semesterRepo,
// WindowService windowService) {
// this.questionRepo = questionRepo;
// this.semesterRepo = semesterRepo;
// this.windowService = windowService;
// }

// @GetMapping
// public String list(@RequestParam(required = false) EvaluationKind kind,
// @RequestParam(required = false) Boolean active,
// Model model) {

// List<Question> questions;
// if (kind == null) {
// questions = questionRepo.findAllByOrderByKindAscOrderNoAsc();
// } else if (active == null) {
// questions = questionRepo.findByKindOrderByOrderNoAsc(kind);
// } else {
// questions = questionRepo.findByKindAndActiveOrderByOrderNoAsc(kind, active);
// }

// model.addAttribute("questions", questions);
// model.addAttribute("kinds", EvaluationKind.values());
// model.addAttribute("types", QuestionType.values());
// model.addAttribute("filterKind", kind);
// model.addAttribute("filterActive", active);
// model.addAttribute("semesters", semesterRepo.findAll());

// return "admin/questions";
// }

// @GetMapping("/new")
// public String createForm(@RequestParam(defaultValue = "STUDENT_FEEDBACK")
// EvaluationKind kind,
// Model model) {
// QuestionForm form = new QuestionForm();
// form.setKind(kind);
// form.setType(QuestionType.RATING);
// form.setScaleMin(1);
// form.setScaleMax(5);
// form.setActive(true);
// form.setOrderNo(questionRepo.maxOrderNoByKind(kind) + 1);

// model.addAttribute("form", form);
// model.addAttribute("kinds", EvaluationKind.values());
// model.addAttribute("types", QuestionType.values());
// return "admin/question_new";
// }

// @PostMapping
// public String create(@Valid @ModelAttribute("form") QuestionForm form,
// BindingResult br,
// Model model) {

// validateForm(form, br);

// if (br.hasErrors()) {
// model.addAttribute("kinds", EvaluationKind.values());
// model.addAttribute("types", QuestionType.values());
// return "admin/question_new";
// }

// Question q = new Question();
// applyForm(q, form);
// questionRepo.save(q);

// return "redirect:/admin/questions?kind=" + form.getKind();
// }

// @GetMapping("/{id}/edit")
// public String editForm(@PathVariable Long id, Model model) {
// Question q = questionRepo.findById(id).orElseThrow();

// QuestionForm form = new QuestionForm();
// form.setKind(q.getKind());
// form.setType(q.getType());
// form.setText(q.getText());
// form.setScaleMin(q.getScaleMin());
// form.setScaleMax(q.getScaleMax());
// form.setOrderNo(q.getOrderNo());
// form.setActive(q.isActive());

// model.addAttribute("id", id);
// model.addAttribute("form", form);
// model.addAttribute("kinds", EvaluationKind.values());
// model.addAttribute("types", QuestionType.values());
// return "admin/question_edit";
// }

// @PostMapping("/{id}")
// public String update(@PathVariable Long id,
// @Valid @ModelAttribute("form") QuestionForm form,
// BindingResult br,
// Model model) {

// validateForm(form, br);

// if (br.hasErrors()) {
// model.addAttribute("id", id);
// model.addAttribute("kinds", EvaluationKind.values());
// model.addAttribute("types", QuestionType.values());
// return "admin/question_edit";
// }

// Question q = questionRepo.findById(id).orElseThrow();
// applyForm(q, form);
// questionRepo.save(q);

// return "redirect:/admin/questions?kind=" + form.getKind();
// }

// @PostMapping("/{id}/toggle")
// public String toggle(@PathVariable Long id) {
// Question q = questionRepo.findById(id).orElseThrow();
// q.setActive(!q.isActive());
// questionRepo.save(q);
// return "redirect:/admin/questions?kind=" + q.getKind();
// }

// @PostMapping("/{id}/delete")
// public String delete(@PathVariable Long id) {
// Question q = questionRepo.findById(id).orElseThrow();
// EvaluationKind kind = q.getKind();

// try {
// questionRepo.delete(q);
// return "redirect:/admin/questions?kind=" + kind + "&msg=deleted";
// } catch (DataIntegrityViolationException ex) {
// // If already used by answers, do soft delete instead
// q.setActive(false);
// questionRepo.save(q);
// return "redirect:/admin/questions?kind=" + kind + "&msg=deactivated";
// }
// }

// // ------------------------------------------------------
// // OPTIONAL: Seed student feedback questions (your Khmer list)
// // ------------------------------------------------------
// @PostMapping("/seed/student-default")
// public String seedStudentDefault() {
// if (questionRepo.countByKind(EvaluationKind.STUDENT_FEEDBACK) > 0) {
// return "redirect:/admin/questions?kind=STUDENT_FEEDBACK&msg=already_seeded";
// }

// int order = 1;

// List<String> rating = List.of(
// "គ្រូមានចំណេះដឹងច្បាស់លាស់លើមុខវិជ្ជា និងមេរៀនដែលបង្រៀន។",
// "សាស្រ្តាចារ្យណែនាំនិស្សិតអំពីប្លង់មុខវិជ្ជាលម្អិត (Course Syllabus)
// ដោយផ្តោតជាពិសេសលើលទ្ធផលសិក្សារំពឹងទុក សកម្មភាពបង្រៀននិងរៀន
// ព្រមទាំងវិធីវាយតម្លៃការសិក្សា។",
// "សាស្ត្រាចារ្យអនុវត្តការបង្រៀនតាមប្លង់មុខវិជ្ជាលម្អិតដែលបានណែនាំដល់និស្សិត។",
// "សាស្ត្រាចារ្យប្រើប្រាស់វិធីសាស្ត្រមានលក្ខណៈចម្រុះ
// ដោយរួមបញ្ចូលការប្រើប្រាស់បច្ចេកវិទ្យាព័ត៌មាន និងទំនាក់ទំនង (ICT)។",
// "សាស្ត្រាចារ្យអនុវត្តវិធីសាស្ត្របង្រៀនដែលផ្តល់ឱកាសឱ្យនិស្សិតធ្វើការអនុវត្តជាក់ស្តែង
// និងធ្វើសកម្មភាពនៅក្រៅថ្នាក់រៀន (Project-based Learning…)។",
// "ខ្លឹមសារមេរៀនត្រូវបានរៀបចំឱ្យមានភាពប្រទាក់ក្រឡា ដើម្បីឱ្យនិស្សិតងាយយល់
// និងអនុវត្តបាន។",
// "សាស្រ្តាចារ្យផ្តល់ឱកាសឱ្យនិស្សិតប្រើប្រាស់ជំនាញគិតវិភាគ និងដោះស្រាយបញ្ហា។",
// "សាស្ត្រាចារ្យប្រើប្រាស់វិធីសាស្រ្តច្រើនបែបក្នុងការវាយតម្លៃការសិក្សារបស់និស្សិត។",
// "សាស្ត្រាចារ្យផ្តល់កិច្ចការក្នុងថ្នាក់ កិច្ចការផ្ទះ និងកិច្ចការស្រាវជ្រាវ
// (Assignment) គ្រប់គ្រាន់សម្រាប់និស្សិតពង្រីក និងអនុវត្តចំណេះដឹង។",
// "សាស្រ្តាចារ្យកែកិច្ចការនិស្សិត
// និងផ្តល់យោបល់ត្រឡប់ដល់និស្សិតទាន់ពេលវេលាសម្រាប់ការកែលម្អចំណុចខ្វះខាត។",
// "ឯកសារ/ស្លាយនៃមេរៀនមានមាតិការច្បាស់លាស់ និងងាយយល់។",
// "មេរៀនមានឯកសារយោងគ្រប់គ្រាន់។",
// "សៀវភៅសិក្សាគោលត្រូវបានកែលម្អ ឬធ្វើបច្ចុប្បន្នភាព។",
// "សាស្រ្តាចារ្យបានបង្រៀនទៀងទាត់តាមម៉ោងកំណត់ (៤៥ម៉ោង)។",
// "សាស្ត្រាចារ្យចូលបង្រៀនតាមម៉ោងកំណត់។",
// "សាស្ត្រាចារ្យចេញពីបង្រៀនតាមម៉ោងកំណត់។",
// "សាស្ត្រាចារ្យផ្សព្វផ្សាយដល់និស្សិតអំពីចក្ខុវិស័យ បេសកកម្ម
// ទស្សនអប់រំរបស់សាកលវិទ្យាល័យ បទបញ្ជាផ្ទៃក្នុងសម្រាប់និស្សិត
// និងព័ត៌មានទូទៅពាក់ព័ន្ធនឹងការសិក្សា។",
// "សាស្ត្រាចារ្យតាមដានវត្ថមាន និងវឌ្ឍនភាពនៃការសិក្សារបស់និស្សិតជាប្រចាំ។",
// "សាស្ត្រាចារ្យផ្តល់ឱកាសឱ្យនិស្សិតគ្រប់រូបបានចូលរួមបញ្ចេញមតិយោបល់
// ឬលើកសំណូមពរនានាពាក់ព័ន្ធនឹងការសិក្សា។",
// "សាស្ត្រាចារ្យផ្តល់សេវាប្រឹក្សាដល់និស្សិតដែលមានការលំបាកក្នុងការសិក្សា
// និងការស្រាវជ្រាវពាក់ព័ន្ធនឹងវិស័យឯកទេស និងមុខវិជ្ជាដែលខ្លួនបង្រៀន។",
// "សាស្ត្រាចារ្យឆ្លើយតប និងទំនាក់ទំនងទាន់ពេលវេលា ប្រកបដោយភាពទទួលខុសត្រូវ
// ចំពោះបញ្ហា និងសំណូមពររបស់និស្សិតពាក់ព័ន្ធទៅនឹងការរៀន និងបង្រៀន។",
// "សាស្ត្រាចារ្យបង្ហាញភាពជាអ្នកដឹកនាំ និងជាគំរូល្អសម្រាប់និស្សិត។",
// "សាស្ត្រាចារ្យប្រកាន់ខ្ជាប់នូវកាយវិការ និងពាក្យពេចន៍ថ្លៃថ្នូរ
// ទៅកាន់និស្សិតគ្រប់រូប។",
// "សាស្ត្រាចារ្យយកចិត្តទុកដាក់ចំពោះនិស្សិតគ្រប់រូប
// និងលើកទឹកចិត្តនិស្សិតឱ្យខិតខំសិក្សា។",
// "សាស្ត្រាចារ្យប្រកាន់ភាពយុត្តិធម៌ក្នុងការវាយតម្លៃការសិក្សារបស់និស្សិត។",
// "សាស្ត្រាចារ្យស្លៀកពាក់សមរម្យ និងមានអនាម័យល្អ ទាំងការបង្រៀនផ្ទាល់
// និងតាមប្រព័ន្ធអនឡាញ។");

// for (String text : rating) {
// Question q = new Question();
// q.setKind(EvaluationKind.STUDENT_FEEDBACK);
// q.setType(QuestionType.RATING);
// q.setText(text);
// q.setScaleMin(1);
// q.setScaleMax(5);
// q.setOrderNo(order++);
// q.setActive(true);
// questionRepo.save(q);
// }

// Question comment = new Question();
// comment.setKind(EvaluationKind.STUDENT_FEEDBACK);
// comment.setType(QuestionType.TEXT);
// comment.setText("មតិយោបល់បន្ថែមដើម្បីកែលម្អការបង្រៀនរបស់សាស្រ្តាចារ្យ");
// comment.setOrderNo(order);
// comment.setActive(true);
// questionRepo.save(comment);

// return "redirect:/admin/questions?kind=STUDENT_FEEDBACK&msg=seeded";
// }

// // ----------------- helpers -----------------
// private void applyForm(Question q, QuestionForm form) {
// q.setKind(form.getKind());
// q.setType(form.getType());
// q.setText(form.getText() != null ? form.getText().trim() : null);
// q.setOrderNo(form.getOrderNo());
// q.setActive(form.isActive());

// if (form.getType() == QuestionType.RATING) {
// q.setScaleMin(form.getScaleMin());
// q.setScaleMax(form.getScaleMax());
// } else {
// q.setScaleMin(null);
// q.setScaleMax(null);
// }
// }

// private void validateForm(QuestionForm form, BindingResult br) {
// if (form.getType() == QuestionType.RATING) {
// if (form.getScaleMin() == null)
// br.rejectValue("scaleMin", "required", "Scale min is required for rating");
// if (form.getScaleMax() == null)
// br.rejectValue("scaleMax", "required", "Scale max is required for rating");
// if (form.getScaleMin() != null && form.getScaleMax() != null &&
// form.getScaleMin() > form.getScaleMax()) {
// br.rejectValue("scaleMax", "invalid", "Scale max must be >= scale min");
// }
// }
// }

// @PostMapping("/{id}/move-up")
// public String moveUp(@PathVariable Long id) {
// Question current = questionRepo.findById(id).orElseThrow();
// EvaluationKind kind = current.getKind();

// Integer currentOrder = current.getOrderNo();
// if (currentOrder == null) {
// // if null, put it at the end
// current.setOrderNo(questionRepo.maxOrderNoByKind(kind) + 1);
// questionRepo.save(current);
// return "redirect:/admin/questions?kind=" + kind;
// }

// var prevOpt =
// questionRepo.findFirstByKindAndOrderNoLessThanOrderByOrderNoDesc(kind,
// currentOrder);
// if (prevOpt.isEmpty()) {
// return "redirect:/admin/questions?kind=" + kind;
// }

// Question prev = prevOpt.get();

// // swap orderNo
// int tmp = prev.getOrderNo();
// prev.setOrderNo(current.getOrderNo());
// current.setOrderNo(tmp);

// questionRepo.save(prev);
// questionRepo.save(current);

// return "redirect:/admin/questions?kind=" + kind;
// }

// @PostMapping("/{id}/move-down")
// public String moveDown(@PathVariable Long id) {
// Question current = questionRepo.findById(id).orElseThrow();
// EvaluationKind kind = current.getKind();

// Integer currentOrder = current.getOrderNo();
// if (currentOrder == null) {
// current.setOrderNo(questionRepo.maxOrderNoByKind(kind) + 1);
// questionRepo.save(current);
// return "redirect:/admin/questions?kind=" + kind;
// }

// var nextOpt =
// questionRepo.findFirstByKindAndOrderNoGreaterThanOrderByOrderNoAsc(kind,
// currentOrder);
// if (nextOpt.isEmpty()) {
// return "redirect:/admin/questions?kind=" + kind;
// }

// Question next = nextOpt.get();

// // swap orderNo
// int tmp = next.getOrderNo();
// next.setOrderNo(current.getOrderNo());
// current.setOrderNo(tmp);

// questionRepo.save(next);
// questionRepo.save(current);

// return "redirect:/admin/questions?kind=" + kind;
// }

// @PostMapping("/normalize")
// public String normalize(@RequestParam EvaluationKind kind) {
// var list = questionRepo.findByKindOrderByOrderNoAsc(kind);
// int i = 1;
// for (Question q : list) {
// q.setOrderNo(i++);
// }
// questionRepo.saveAll(list);
// return "redirect:/admin/questions?kind=" + kind + "&msg=normalized";
// }

// @PostMapping("/publish")
// public String publishAll(@RequestParam EvaluationKind kind) {
// questionRepo.updateActiveByKind(kind, true);
// return "redirect:/admin/questions?kind=" + kind + "&msg=published";
// }

// @PostMapping("/unpublish")
// public String unpublishAll(@RequestParam EvaluationKind kind) {
// questionRepo.updateActiveByKind(kind, false);
// return "redirect:/admin/questions?kind=" + kind + "&msg=unpublished";
// }

// @PostMapping("/window/open")
// public String openWindow(@RequestParam Long semesterId,
// @RequestParam EvaluationKind kind,
// @RequestParam(defaultValue = "7") int days) {
// windowService.openNowForDays(semesterId, kind, days);
// return "redirect:/admin/questions?kind=" + kind + "&msg=window_opened";
// }

// @PostMapping("/window/close")
// public String closeWindow(@RequestParam Long semesterId,
// @RequestParam EvaluationKind kind) {
// windowService.closeNow(semesterId, kind);
// return "redirect:/admin/questions?kind=" + kind + "&msg=window_closed";
// }

// }
