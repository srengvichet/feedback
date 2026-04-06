package kh.edu.num.feedback.web.admin;

import kh.edu.num.feedback.domain.entity.Course;
import kh.edu.num.feedback.domain.repo.CourseRepository;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.*;

@Controller
@RequestMapping("/admin/courses")
public class AdminCourseController {

  private final CourseRepository courseRepo;

  public AdminCourseController(CourseRepository courseRepo) {
    this.courseRepo = courseRepo;
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  private void populate(Model model, List<Course> all) {
    List<Course> y1s1 = new ArrayList<>(), y1s2 = new ArrayList<>();
    List<Course> y2s1 = new ArrayList<>(), y2s2 = new ArrayList<>();
    List<Course> y3s1 = new ArrayList<>(), y3s2 = new ArrayList<>();
    List<Course> y4s1 = new ArrayList<>(), y4s2 = new ArrayList<>();
    List<Course> unassigned = new ArrayList<>();

    for (Course c : all) {
      Integer y = c.getStudyYear(), s = c.getSemesterNo();
      if (y == null || s == null || y < 1 || y > 4 || s < 1 || s > 2) { unassigned.add(c); continue; }
      if      (y == 1 && s == 1) y1s1.add(c);
      else if (y == 1 && s == 2) y1s2.add(c);
      else if (y == 2 && s == 1) y2s1.add(c);
      else if (y == 2 && s == 2) y2s2.add(c);
      else if (y == 3 && s == 1) y3s1.add(c);
      else if (y == 3 && s == 2) y3s2.add(c);
      else if (y == 4 && s == 1) y4s1.add(c);
      else if (y == 4 && s == 2) y4s2.add(c);
    }

    model.addAttribute("courses", all);
    model.addAttribute("unassigned", unassigned);
    model.addAttribute("y1s1", y1s1); model.addAttribute("y1s2", y1s2);
    model.addAttribute("y2s1", y2s1); model.addAttribute("y2s2", y2s2);
    model.addAttribute("y3s1", y3s1); model.addAttribute("y3s2", y3s2);
    model.addAttribute("y4s1", y4s1); model.addAttribute("y4s2", y4s2);
    model.addAttribute("cnt1", y1s1.size() + y1s2.size());
    model.addAttribute("cnt2", y2s1.size() + y2s2.size());
    model.addAttribute("cnt3", y3s1.size() + y3s2.size());
    model.addAttribute("cnt4", y4s1.size() + y4s2.size());
    model.addAttribute("newCourse", new Course());
  }

  private String cellStr(Cell cell) {
    if (cell == null) return "";
    return switch (cell.getCellType()) {
      case STRING  -> cell.getStringCellValue().trim();
      case NUMERIC -> {
        double v = cell.getNumericCellValue();
        yield (v == Math.floor(v)) ? String.valueOf((long) v) : String.valueOf(v);
      }
      case FORMULA -> {
        try { yield String.valueOf((long) cell.getNumericCellValue()); }
        catch (Exception e) { yield cell.getStringCellValue().trim(); }
      }
      default -> "";
    };
  }

  private Integer parseIntCell(Cell cell) {
    String s = cellStr(cell);
    if (s.isBlank()) return null;
    try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
  }

  // ── CRUD ───────────────────────────────────────────────────────────────────

  @GetMapping
  public String list(Model model) {
    populate(model, courseRepo.findAllByOrderByStudyYearAscSemesterNoAscCodeAsc());
    return "admin/courses";
  }

  @PostMapping
  public String create(@ModelAttribute("newCourse") Course c) {
    if (c.getCode() == null || c.getCode().isBlank()) return "redirect:/admin/courses?error";
    if (c.getName() == null || c.getName().isBlank()) return "redirect:/admin/courses?error";
    c.setCode(c.getCode().trim().toUpperCase());
    c.setName(c.getName().trim());
    courseRepo.save(c);
    return "redirect:/admin/courses?msg=created";
  }

  @PostMapping("/{id}/update")
  public String update(@PathVariable Long id,
                       @RequestParam String code,
                       @RequestParam String name,
                       @RequestParam(required = false) Integer credit,
                       @RequestParam(required = false) Integer studyYear,
                       @RequestParam(required = false) Integer semesterNo) {
    if (code == null || code.isBlank() || name == null || name.isBlank())
      return "redirect:/admin/courses?error";
    Course c = courseRepo.findById(id).orElseThrow();
    c.setCode(code.trim().toUpperCase());
    c.setName(name.trim());
    c.setCredit(credit);
    c.setStudyYear(studyYear);
    c.setSemesterNo(semesterNo);
    courseRepo.save(c);
    return "redirect:/admin/courses?msg=updated";
  }

  @PostMapping("/{id}/delete")
  public String delete(@PathVariable Long id) {
    courseRepo.deleteById(id);
    return "redirect:/admin/courses?msg=deleted";
  }

  // ── EXPORT ─────────────────────────────────────────────────────────────────

  @GetMapping("/export")
  public ResponseEntity<byte[]> export() throws Exception {
    List<Course> all = courseRepo.findAllByOrderByStudyYearAscSemesterNoAscCodeAsc();

    try (XSSFWorkbook wb = new XSSFWorkbook();
         ByteArrayOutputStream out = new ByteArrayOutputStream()) {

      // Styles
      XSSFCellStyle headerStyle = wb.createCellStyle();
      XSSFFont headerFont = wb.createFont();
      headerFont.setBold(true);
      headerFont.setFontHeightInPoints((short) 11);
      headerStyle.setFont(headerFont);
      headerStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte)37,(byte)99,(byte)235}, null));
      headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
      headerFont.setColor(IndexedColors.WHITE.getIndex());
      headerStyle.setAlignment(HorizontalAlignment.CENTER);
      headerStyle.setBorderBottom(BorderStyle.THIN);

      XSSFCellStyle dataStyle = wb.createCellStyle();
      dataStyle.setBorderBottom(BorderStyle.THIN);
      dataStyle.setBorderTop(BorderStyle.THIN);
      dataStyle.setBorderLeft(BorderStyle.THIN);
      dataStyle.setBorderRight(BorderStyle.THIN);

      XSSFCellStyle centerStyle = wb.createCellStyle();
      centerStyle.cloneStyleFrom(dataStyle);
      centerStyle.setAlignment(HorizontalAlignment.CENTER);

      XSSFSheet sheet = wb.createSheet("Courses");
      sheet.setColumnWidth(0, 5 * 256);    // No
      sheet.setColumnWidth(1, 6 * 256);    // Year
      sheet.setColumnWidth(2, 8 * 256);    // Semester
      sheet.setColumnWidth(3, 12 * 256);   // Code
      sheet.setColumnWidth(4, 40 * 256);   // Name
      sheet.setColumnWidth(5, 8 * 256);    // Credit

      // Header row
      String[] headers = {"No", "Year", "Semester", "Code", "Course Name", "Credit"};
      Row hRow = sheet.createRow(0);
      for (int i = 0; i < headers.length; i++) {
        Cell cell = hRow.createCell(i);
        cell.setCellValue(headers[i]);
        cell.setCellStyle(headerStyle);
      }

      // Data rows
      int rowIdx = 1;
      for (Course c : all) {
        Row row = sheet.createRow(rowIdx++);
        createCell(row, 0, rowIdx - 1, centerStyle);
        createCell(row, 1, c.getStudyYear() != null ? c.getStudyYear() : 0, centerStyle);
        createCell(row, 2, c.getSemesterNo() != null ? c.getSemesterNo() : 0, centerStyle);
        createCell(row, 3, c.getCode(), centerStyle);
        createCell(row, 4, c.getName(), dataStyle);
        createCell(row, 5, c.getCredit() != null ? c.getCredit() : 0, centerStyle);
      }

      // Freeze header row
      sheet.createFreezePane(0, 1);

      wb.write(out);
      byte[] bytes = out.toByteArray();

      HttpHeaders httpHeaders = new HttpHeaders();
      httpHeaders.setContentType(MediaType.parseMediaType(
          "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
      httpHeaders.setContentDispositionFormData("attachment", "courses.xlsx");
      return ResponseEntity.ok().headers(httpHeaders).body(bytes);
    }
  }

  private void createCell(Row row, int col, Object value, CellStyle style) {
    Cell cell = row.createCell(col);
    if (value instanceof Number n) cell.setCellValue(n.doubleValue());
    else cell.setCellValue(value != null ? value.toString() : "");
    cell.setCellStyle(style);
  }

  // ── EXPORT TEMPLATE ────────────────────────────────────────────────────────

  @GetMapping("/template")
  public ResponseEntity<byte[]> template() throws Exception {
    try (XSSFWorkbook wb = new XSSFWorkbook();
         ByteArrayOutputStream out = new ByteArrayOutputStream()) {

      XSSFCellStyle headerStyle = wb.createCellStyle();
      XSSFFont hf = wb.createFont();
      hf.setBold(true); hf.setColor(IndexedColors.WHITE.getIndex());
      headerStyle.setFont(hf);
      headerStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte)37,(byte)99,(byte)235}, null));
      headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
      headerStyle.setAlignment(HorizontalAlignment.CENTER);

      XSSFCellStyle noteStyle = wb.createCellStyle();
      XSSFFont nf = wb.createFont();
      nf.setItalic(true); nf.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
      noteStyle.setFont(nf);

      XSSFSheet sheet = wb.createSheet("Courses");
      sheet.setColumnWidth(0, 6 * 256);
      sheet.setColumnWidth(1, 8 * 256);
      sheet.setColumnWidth(2, 12 * 256);
      sheet.setColumnWidth(3, 40 * 256);
      sheet.setColumnWidth(4, 8 * 256);

      // Header
      String[] headers = {"Year (1-4)", "Semester (1-2)", "Code", "Course Name", "Credit"};
      Row hRow = sheet.createRow(0);
      for (int i = 0; i < headers.length; i++) {
        Cell cell = hRow.createCell(i);
        cell.setCellValue(headers[i]);
        cell.setCellStyle(headerStyle);
      }

      // Drop-down validation for Year (col 0) and Semester (col 1)
      DataValidationHelper dvh = sheet.getDataValidationHelper();
      addDropdown(sheet, dvh, "1,2,3,4", 1, 500, 0, 0);
      addDropdown(sheet, dvh, "1,2",     1, 500, 1, 1);

      // Sample rows
      String[][] samples = {
        {"1","1","IT101","Introduction to Programming","3"},
        {"1","2","IT102","Data Structures","3"},
        {"2","1","IT201","Database Systems","3"},
      };
      int r = 1;
      for (String[] s : samples) {
        Row row = sheet.createRow(r++);
        for (int i = 0; i < s.length; i++) row.createCell(i).setCellValue(s[i]);
      }

      // Note row
      Row noteRow = sheet.createRow(r + 1);
      Cell noteCell = noteRow.createCell(0);
      noteCell.setCellValue("Note: Year 1-4, Semester 1-2. Code must be unique. Credit is optional.");
      noteCell.setCellStyle(noteStyle);

      sheet.createFreezePane(0, 1);
      wb.write(out);

      HttpHeaders httpHeaders = new HttpHeaders();
      httpHeaders.setContentType(MediaType.parseMediaType(
          "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
      httpHeaders.setContentDispositionFormData("attachment", "courses_template.xlsx");
      return ResponseEntity.ok().headers(httpHeaders).body(out.toByteArray());
    }
  }

  private void addDropdown(XSSFSheet sheet, DataValidationHelper dvh,
                            String formula, int r1, int r2, int c1, int c2) {
    DataValidationConstraint constraint = dvh.createExplicitListConstraint(formula.split(","));
    CellRangeAddressList range = new CellRangeAddressList(r1, r2, c1, c2);
    DataValidation dv = dvh.createValidation(constraint, range);
    dv.setShowErrorBox(true);
    sheet.addValidationData(dv);
  }

  // ── IMPORT ─────────────────────────────────────────────────────────────────

  @PostMapping("/import")
  public String importExcel(@RequestParam MultipartFile file) {
    if (file.isEmpty()) return "redirect:/admin/courses?msg=import_empty";

    int created = 0, updated = 0, skipped = 0, errors = 0;

    try (XSSFWorkbook wb = new XSSFWorkbook(file.getInputStream())) {
      Sheet sheet = wb.getSheetAt(0);

      // Read header row to map column names → indices
      Row headerRow = sheet.getRow(0);
      if (headerRow == null) return "redirect:/admin/courses?msg=import_empty";

      Map<String, Integer> colIdx = new HashMap<>();
      for (Cell cell : headerRow) {
        String h = cell.getStringCellValue().trim().toLowerCase();
        colIdx.put(h, cell.getColumnIndex());
      }

      // Support both export format (has "no" col) and template format (no "no" col)
      Integer iYear   = colIdx.getOrDefault("year",    colIdx.getOrDefault("year (1-4)", null));
      Integer iSem    = colIdx.getOrDefault("semester",colIdx.getOrDefault("semester (1-2)", null));
      Integer iCode   = colIdx.getOrDefault("code",    null);
      Integer iName   = colIdx.getOrDefault("course name", colIdx.getOrDefault("name", null));
      Integer iCredit = colIdx.getOrDefault("credit",  null);

      if (iCode == null || iName == null)
        return "redirect:/admin/courses?msg=import_badformat";

      for (int i = 1; i <= sheet.getLastRowNum(); i++) {
        Row row = sheet.getRow(i);
        if (row == null) continue;

        String code = cellStr(row.getCell(iCode)).toUpperCase();
        String name = cellStr(row.getCell(iName));
        if (code.isBlank() || name.isBlank()) { skipped++; continue; }

        Integer year   = (iYear   != null) ? parseIntCell(row.getCell(iYear))   : null;
        Integer sem    = (iSem    != null) ? parseIntCell(row.getCell(iSem))    : null;
        Integer credit = (iCredit != null) ? parseIntCell(row.getCell(iCredit)) : null;

        // Validate year/sem ranges if provided
        if (year != null && (year < 1 || year > 4)) { errors++; continue; }
        if (sem  != null && (sem  < 1 || sem  > 2)) { errors++; continue; }

        try {
          var existing = courseRepo.findAll().stream()
              .filter(c -> code.equalsIgnoreCase(c.getCode()))
              .findFirst();

          if (existing.isPresent()) {
            Course c = existing.get();
            c.setName(name);
            if (year   != null) c.setStudyYear(year);
            if (sem    != null) c.setSemesterNo(sem);
            if (credit != null) c.setCredit(credit);
            courseRepo.save(c);
            updated++;
          } else {
            Course c = new Course();
            c.setCode(code);
            c.setName(name);
            c.setStudyYear(year);
            c.setSemesterNo(sem);
            c.setCredit(credit);
            courseRepo.save(c);
            created++;
          }
        } catch (Exception e) {
          errors++;
        }
      }
    } catch (Exception e) {
      return "redirect:/admin/courses?msg=import_error";
    }

    return "redirect:/admin/courses?msg=imported_" + created
        + "_updated_" + updated
        + "_skipped_" + skipped
        + "_errors_" + errors;
  }
}
