package kh.edu.num.feedback.web.admin;

import jakarta.validation.Valid;
import kh.edu.num.feedback.domain.entity.Role;
import kh.edu.num.feedback.domain.entity.UserAccount;
import kh.edu.num.feedback.domain.repo.UserAccountRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/teachers")
public class AdminTeacherController {

  private final UserAccountRepository userRepo;
  private final PasswordEncoder passwordEncoder;

  public AdminTeacherController(UserAccountRepository userRepo, PasswordEncoder passwordEncoder) {
    this.userRepo = userRepo;
    this.passwordEncoder = passwordEncoder;
  }

  @GetMapping
  public String list(Model model,
      @RequestParam(required = false) String msg,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String enabled, // <-- String, not Boolean
      @RequestParam(required = false) String department,
      @RequestParam(required = false, defaultValue = "1") Integer page,
      @RequestParam(required = false, defaultValue = "20") Integer size) {

    List<UserAccount> all = userRepo.findByRole(Role.TEACHER); // :contentReference[oaicite:3]{index=3}
    int allCount = all.size();

    // parse enabled safely
    // Boolean enabledFilter = null;
    final Boolean enabledFilter = (enabled == null || enabled.isBlank())
        ? null
        : Boolean.valueOf(enabled.trim());

    String qq = (q == null) ? "" : q.trim().toLowerCase();
    String dept = (department == null) ? "" : department.trim().toLowerCase();

    List<UserAccount> filtered = all.stream()
        .filter(t -> enabledFilter == null || t.isEnabled() == enabledFilter)
        .filter(t -> dept.isEmpty() ||
            ((t.getDepartment() == null ? "" : t.getDepartment())
                .toLowerCase().contains(dept)))
        .filter(t -> {
          if (qq.isEmpty())
            return true;
          String u = (t.getUsername() == null ? "" : t.getUsername().toLowerCase());
          String fn = (t.getFullName() == null ? "" : t.getFullName().toLowerCase());
          String em = (t.getEmail() == null ? "" : t.getEmail().toLowerCase());
          String ph = (t.getPhone() == null ? "" : t.getPhone().toLowerCase());
          return u.contains(qq) || fn.contains(qq) || em.contains(qq) || ph.contains(qq);
        })
        .toList();

    int safeSize = (size == null) ? 20 : size;
    if (safeSize != 10 && safeSize != 20 && safeSize != 50 && safeSize != 100)
      safeSize = 20;

    int safePage = (page == null || page < 1) ? 1 : page;

    int totalRows = filtered.size();
    int totalPages = (int) Math.ceil(totalRows / (double) safeSize);
    if (totalPages > 0 && safePage > totalPages)
      safePage = totalPages;

    int fromIndex = (safePage - 1) * safeSize;
    int toIndex = Math.min(fromIndex + safeSize, totalRows);

    List<UserAccount> teachers = (fromIndex >= totalRows)
        ? java.util.Collections.emptyList()
        : filtered.subList(fromIndex, toIndex);

    model.addAttribute("teachers", teachers);
    model.addAttribute("form", new TeacherCreateForm());
    model.addAttribute("msg", msg);

    // filters for UI
    model.addAttribute("q", q);
    model.addAttribute("filterEnabled", enabledFilter); // Boolean for UI state
    model.addAttribute("filterDepartment", department);

    // pagination
    model.addAttribute("page", safePage);
    model.addAttribute("size", safeSize);
    model.addAttribute("totalRows", totalRows);
    model.addAttribute("totalPages", totalPages);

    // NEW: show true DB total (helps you confirm)
    model.addAttribute("allCount", allCount);

    // Gender & department stats (computed from ALL teachers, not filtered)
    List<UserAccount> allTeachers = userRepo.findByRole(Role.TEACHER);
    long maleCount   = allTeachers.stream().filter(t -> "Male".equalsIgnoreCase(t.getGender())).count();
    long femaleCount = allTeachers.stream().filter(t -> "Female".equalsIgnoreCase(t.getGender())).count();
    long deptCount   = allTeachers.stream()
        .map(UserAccount::getDepartment).filter(d -> d != null && !d.isBlank())
        .distinct().count();
    model.addAttribute("maleCount", maleCount);
    model.addAttribute("femaleCount", femaleCount);
    model.addAttribute("deptCount", deptCount);

    return "admin/teachers";
  }

  @PostMapping
  public String create(@Valid @ModelAttribute("form") TeacherCreateForm form,
      BindingResult br,
      Model model) {

    if (br.hasErrors()) {
      model.addAttribute("teachers", userRepo.findByRole(Role.TEACHER));
      return "admin/teachers";
    }

    String username = form.getUsername().trim();

    if (userRepo.existsByUsername(username)) {
      model.addAttribute("teachers", userRepo.findByRole(Role.TEACHER));
      model.addAttribute("msg", "duplicate");
      return "admin/teachers";
    }

    UserAccount t = new UserAccount();
    t.setUsername(username);
    t.setPasswordHash(passwordEncoder.encode(form.getPassword())); // matches entity field passwordHash
    t.setRole(Role.TEACHER);
    t.setEnabled(true);
    // ===== New Profile Fields =====
    t.setFullName(form.getFullName());
    t.setEmail(form.getEmail());
    t.setPhone(form.getPhone());
    t.setDepartment(form.getDepartment());
    t.setPosition(form.getPosition());
    t.setAvatarUrl(form.getAvatarUrl());
    t.setGender(form.getGender());

    userRepo.save(t);
    return "redirect:/admin/teachers?msg=created";
  }

  @PostMapping("/{id}/toggle")
  public String toggle(@PathVariable Long id,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) Boolean enabled,
      @RequestParam(required = false) String department) {

    var u = userRepo.findById(id).orElseThrow();
    if (u.getRole() == Role.TEACHER) {
      u.setEnabled(!u.isEnabled());
      userRepo.save(u);
    }

    return "redirect:/admin/teachers"
        + "?page=" + (page == null ? 1 : page)
        + "&size=" + (size == null ? 20 : size)
        + (q != null ? "&q=" + java.net.URLEncoder.encode(q, java.nio.charset.StandardCharsets.UTF_8) : "")
        + (enabled != null ? "&enabled=" + enabled : "")
        + (department != null
            ? "&department=" + java.net.URLEncoder.encode(department, java.nio.charset.StandardCharsets.UTF_8)
            : "");
  }

  @GetMapping("/{id}/edit")
  public String editPage(@PathVariable Long id, Model model) {
    var teacher = userRepo.findById(id).orElseThrow();
    model.addAttribute("t", teacher);
    return "admin/teacher_edit.html";
  }

  @PostMapping("/{id}/edit")
  public String saveEdit(@PathVariable Long id,
      @ModelAttribute UserAccount form) {

    var u = userRepo.findById(id).orElseThrow();

    u.setUsername(form.getUsername());
    u.setFullName(form.getFullName());
    u.setEmail(form.getEmail());
    u.setPhone(form.getPhone());
    u.setDepartment(form.getDepartment());
    u.setPosition(form.getPosition());
    u.setAvatarUrl(form.getAvatarUrl());
    u.setGender(form.getGender());
    u.setEnabled(form.isEnabled());

    userRepo.save(u);

    return "redirect:/admin/teachers?msg=updated";
  }

  // -------------------------
  // EXPORT
  // -------------------------
  @GetMapping("/export")
  public ResponseEntity<byte[]> exportCsv() {
    List<UserAccount> teachers = userRepo.findByRole(Role.TEACHER);
    teachers.sort(Comparator.comparing(UserAccount::getUsername, Comparator.nullsLast(String::compareTo)));

    String bom = "\uFEFF";
    StringBuilder sb = new StringBuilder();
    sb.append("username,fullName,gender,department,position,email,phone\n");

    for (UserAccount t : teachers) {
      sb.append(csvEscape(safe(t.getUsername()))).append(",")
          .append(csvEscape(safe(t.getFullName()))).append(",")
          .append(csvEscape(safe(t.getGender()))).append(",")
          .append(csvEscape(safe(t.getDepartment()))).append(",")
          .append(csvEscape(safe(t.getPosition()))).append(",")
          .append(csvEscape(safe(t.getEmail()))).append(",")
          .append(csvEscape(safe(t.getPhone())))
          .append("\n");
    }

    String filename = "teachers_export_" +
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".csv";

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .body((bom + sb).getBytes(StandardCharsets.UTF_8));
  }

  // -------------------------
  // TEMPLATE
  // -------------------------
  @GetMapping("/template")
  public ResponseEntity<byte[]> downloadTemplate() {
    String bom = "\uFEFF";
    String csv = bom + "username,fullName,gender,department,position,email,phone,password\n"
        + "t.sokdara,Sok Dara,Male,Faculty of IT,Lecturer,sokdara@num.edu.kh,012345678,changeme123\n"
        + "t.channary,Chan Nary,Female,Faculty of Management,Assistant Professor,channary@num.edu.kh,098765432,changeme123\n";

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"teacher_import_template.csv\"")
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .body(csv.getBytes(StandardCharsets.UTF_8));
  }

  // -------------------------
  // IMPORT
  // -------------------------
  @PostMapping("/import")
  public String importCsv(@RequestParam("file") MultipartFile file) {
    if (file == null || file.isEmpty()) {
      return "redirect:/admin/teachers?msg=empty_file";
    }

    int imported = 0;
    int skipped = 0;
    int errors = 0;

    try (BufferedReader br = new BufferedReader(
        new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

      String firstLine = br.readLine();
      if (firstLine == null) {
        return "redirect:/admin/teachers?msg=empty_file";
      }

      // Strip BOM if present
      if (firstLine.startsWith("\uFEFF")) {
        firstLine = firstLine.substring(1);
      }

      char delim = firstLine.contains(",") ? ',' : ';';
      List<String> firstFields = parseCsvLine(firstLine, delim);
      Map<String, Integer> headerIdx = buildHeaderIndex(firstFields);

      // Skip first line if it looks like a header
      boolean hasHeader = headerIdx.containsKey("username");
      List<String> allLines = new ArrayList<>();
      if (!hasHeader) {
        allLines.add(firstLine);
      }

      String line;
      while ((line = br.readLine()) != null) {
        if (!line.isBlank()) allLines.add(line);
      }

      for (String dataLine : allLines) {
        List<String> fields = parseCsvLine(dataLine, delim);
        String username = stripBom(getField(fields, headerIdx.get("username"))).trim();
        if (username.isBlank()) { errors++; continue; }

        if (userRepo.existsByUsername(username)) { skipped++; continue; }

        String fullName   = getField(fields, headerIdx.get("fullname")).trim();
        String gender     = normalizeGender(getField(fields, headerIdx.get("gender")).trim());
        String department = getField(fields, headerIdx.get("department")).trim();
        String position   = getField(fields, headerIdx.get("position")).trim();
        String email      = getField(fields, headerIdx.get("email")).trim();
        String phone      = getField(fields, headerIdx.get("phone")).trim();
        String password   = getField(fields, headerIdx.get("password")).trim();
        if (password.isBlank()) password = username; // default password = username

        UserAccount t = new UserAccount();
        t.setUsername(username);
        t.setPasswordHash(passwordEncoder.encode(password));
        t.setRole(Role.TEACHER);
        t.setEnabled(true);
        if (!fullName.isEmpty())   t.setFullName(fullName);
        if (!gender.isEmpty())     t.setGender(gender);
        if (!department.isEmpty()) t.setDepartment(department);
        if (!position.isEmpty())   t.setPosition(position);
        if (!email.isEmpty())      t.setEmail(email);
        if (!phone.isEmpty())      t.setPhone(phone);

        userRepo.save(t);
        imported++;
      }

    } catch (Exception e) {
      return "redirect:/admin/teachers?msg=import_failed";
    }

    return "redirect:/admin/teachers?msg=imported_" + imported + "_skipped_" + skipped + "_errors_" + errors;
  }

  private String normalizeGender(String raw) {
    if (raw == null || raw.isBlank()) return "";
    String v = raw.trim().toLowerCase();
    if (v.equals("male") || v.equals("m") || v.equals("បុរស")) return "Male";
    if (v.equals("female") || v.equals("f") || v.equals("នារី") || v.equals("ស្រី")) return "Female";
    if (v.equals("other")) return "Other";
    return raw.trim();
  }

  // -------------------------
  // CSV helpers
  // -------------------------
  private Map<String, Integer> buildHeaderIndex(List<String> headers) {
    Map<String, Integer> idx = new HashMap<>();
    for (int i = 0; i < headers.size(); i++) {
      String key = headers.get(i).trim().toLowerCase().replaceAll("[^a-z0-9]", "");
      idx.put(key, i);
    }
    return idx;
  }

  private String getField(List<String> fields, Integer i) {
    if (i == null || i >= fields.size()) return "";
    String v = fields.get(i);
    return v == null ? "" : v;
  }

  private String safe(String s) { return s == null ? "" : s; }

  private String stripBom(String s) {
    if (s != null && s.startsWith("\uFEFF")) return s.substring(1);
    return s == null ? "" : s;
  }

  private String csvEscape(String v) {
    if (v == null) return "";
    if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
      return "\"" + v.replace("\"", "\"\"") + "\"";
    }
    return v;
  }

  private List<String> parseCsvLine(String line, char delim) {
    List<String> result = new ArrayList<>();
    StringBuilder cur = new StringBuilder();
    boolean inQuotes = false;
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (c == '"') {
        if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
          cur.append('"'); i++;
        } else {
          inQuotes = !inQuotes;
        }
      } else if (c == delim && !inQuotes) {
        result.add(cur.toString());
        cur.setLength(0);
      } else {
        cur.append(c);
      }
    }
    result.add(cur.toString());
    return result;
  }
}
