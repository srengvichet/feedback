package kh.edu.num.feedback.web.admin;

import kh.edu.num.feedback.domain.entity.Cohort;
import kh.edu.num.feedback.domain.entity.Role;
import kh.edu.num.feedback.domain.entity.ShiftTime;
import kh.edu.num.feedback.domain.entity.StudentRegistry;
import kh.edu.num.feedback.domain.entity.UserAccount;
import kh.edu.num.feedback.domain.repo.CohortGroupRepository;
import kh.edu.num.feedback.domain.repo.CohortRepository;
import kh.edu.num.feedback.domain.repo.StudentRegistryRepository;
import kh.edu.num.feedback.domain.repo.UserAccountRepository;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/students/registry")
public class AdminStudentRegistryController {

    private final StudentRegistryRepository registryRepo;
    private final CohortRepository cohortRepo;
    private final CohortGroupRepository cohortGroupRepo;

    // ✅ NEW: sync to users table
    private final UserAccountRepository userRepo;
    private final PasswordEncoder passwordEncoder;

    private static final ZoneId ZONE = ZoneId.of("Asia/Phnom_Penh");

    public AdminStudentRegistryController(
            StudentRegistryRepository registryRepo,
            CohortRepository cohortRepo,
            CohortGroupRepository cohortGroupRepo,
            UserAccountRepository userRepo,
            PasswordEncoder passwordEncoder) {
        this.registryRepo = registryRepo;
        this.cohortRepo = cohortRepo;
        this.cohortGroupRepo = cohortGroupRepo;
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public String page(Model model,
            @RequestParam(required = false) Integer cohortNo,
            @RequestParam(required = false) Integer groupNo,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Boolean claimed,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String faculty,
            @RequestParam(required = false) String msg,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size,
            @RequestParam(required = false, defaultValue = "false") Boolean showAll) {

        // Keep your existing filtering
        List<StudentRegistry> all = filteredRows(cohortNo, groupNo, active, claimed, q, faculty);

        int total = all.size();

        int safePage = (page == null || page < 1) ? 1 : page;

        List<StudentRegistry> rows;
        int safeSize;
        int totalPages;

        if (Boolean.TRUE.equals(showAll)) {
            rows = all;
            safeSize = total == 0 ? 20 : total;
            totalPages = 1;
            safePage = 1;
        } else {
            // allow bigger page sizes
            safeSize = (size == null || size < 5) ? 20 : Math.min(size, 500);

            totalPages = (int) Math.ceil(total / (double) safeSize);
            if (totalPages > 0 && safePage > totalPages) {
                safePage = totalPages;
            }

            int fromIndex = (safePage - 1) * safeSize;
            int toIndex = Math.min(fromIndex + safeSize, total);

            rows = (fromIndex >= total)
                    ? Collections.emptyList()
                    : all.subList(fromIndex, toIndex);
        }

        model.addAttribute("rows", rows);
        model.addAttribute("cohorts", cohortRepo.findAllByOrderByCohortNoAsc());
        model.addAttribute("shifts", ShiftTime.values());

        model.addAttribute("filterCohortNo", cohortNo);
        model.addAttribute("filterGroupNo", groupNo);
        model.addAttribute("filterActive", active);
        model.addAttribute("filterClaimed", claimed);
        model.addAttribute("q", q);
        model.addAttribute("filterFaculty", faculty);
        model.addAttribute("faculties", cohortRepo.findAllByOrderByCohortNoAsc().stream()
                .map(c -> c.getFaculty())
                .filter(f -> f != null && !f.isBlank())
                .distinct().sorted().toList());
        model.addAttribute("msg", msg);

        model.addAttribute("page", safePage);
        model.addAttribute("size", safeSize);
        model.addAttribute("totalRows", total);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("showAll", Boolean.TRUE.equals(showAll));

        // KPI stats from full DB (not filtered)
        List<StudentRegistry> allRecords = registryRepo.findAll();
        model.addAttribute("allCount",     allRecords.size());
        model.addAttribute("activeCount",  allRecords.stream().filter(StudentRegistry::isActive).count());
        model.addAttribute("claimedCount", allRecords.stream().filter(StudentRegistry::isClaimed).count());

        return "admin/student_registry";
    }

    @GetMapping("/{id}/edit")
    public String editPage(@PathVariable Long id, Model model) {
        StudentRegistry record = registryRepo.findById(id).orElseThrow();
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", StudentRegistryEditForm.from(record));
        }
        populateEditModel(model, record);
        return "admin/student_registry_edit";
    }

    @PostMapping("/{id}/edit")
    public String saveEdit(@PathVariable Long id,
            @ModelAttribute("form") StudentRegistryEditForm form,
            Model model) {
        StudentRegistry record = registryRepo.findById(id).orElseThrow();

        String studentLogin = trimToNull(form.getStudentLogin());
        if (studentLogin == null) {
            return renderEditError(model, record, form, "Student login is required.");
        }

        StudentRegistry duplicateRegistry = registryRepo.findByStudentLoginIgnoreCase(studentLogin).orElse(null);
        if (duplicateRegistry != null && !duplicateRegistry.getId().equals(record.getId())) {
            return renderEditError(model, record, form, "This student login is already used in the registry.");
        }

        UserAccount conflictingUser = userRepo.findByUsername(studentLogin).orElse(null);
        if (conflictingUser != null && (record.getUser() == null || !conflictingUser.getId().equals(record.getUser().getId()))) {
            return renderEditError(model, record, form, "This student login is already used by another account.");
        }

        Cohort cohort = null;
        if (form.getCohortId() != null) {
            cohort = cohortRepo.findById(form.getCohortId()).orElse(null);
            if (cohort == null) {
                return renderEditError(model, record, form, "Selected cohort was not found.");
            }
        }

        ShiftTime shift = null;
        if (trimToNull(form.getShiftTime()) != null) {
            try {
                shift = ShiftTime.valueOf(form.getShiftTime().trim());
            } catch (IllegalArgumentException ex) {
                return renderEditError(model, record, form, "Selected shift is invalid.");
            }
        }

        LocalDate dateOfBirth = null;
        if (trimToNull(form.getDateOfBirth()) != null) {
            dateOfBirth = parseDate(form.getDateOfBirth());
            if (dateOfBirth == null) {
                return renderEditError(model, record, form, "Date of birth must be a valid date.");
            }
        }

        String firstName = trimToNull(form.getFirstName());
        String lastName = trimToNull(form.getLastName());
        String fullName = trimToNull(form.getFullName());
        if (fullName == null && (firstName != null || lastName != null)) {
            fullName = java.util.stream.Stream.of(firstName, lastName)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining(" "));
        }

        record.setStudentLogin(studentLogin);
        record.setFullName(fullName);
        record.setFirstName(firstName);
        record.setLastName(lastName);
        record.setFirstNameKh(trimToNull(form.getFirstNameKh()));
        record.setLastNameKh(trimToNull(form.getLastNameKh()));
        record.setGender(trimToNull(form.getGender()));
        record.setDateOfBirth(dateOfBirth);
        record.setPhone(trimToNull(form.getPhone()));
        record.setEmail(trimToNull(form.getEmail()));
        record.setRemark(trimToNull(form.getRemark()));
        record.setCohort(cohort);
        record.setGroupNo(form.getGroupNo());
        record.setClassName(trimToNull(form.getClassName()));
        record.setShiftTime(shift);

        registryRepo.save(record);
        syncRegistryUserAccount(record);

        return "redirect:/admin/students/registry?msg=updated";
    }
    // @GetMapping
    // public String page(Model model,
    // @RequestParam(required = false) Integer cohortNo,
    // @RequestParam(required = false) Integer groupNo,
    // @RequestParam(required = false) Boolean active,
    // @RequestParam(required = false) Boolean claimed,
    // @RequestParam(required = false) String q,
    // @RequestParam(required = false) String msg,
    // @RequestParam(required = false, defaultValue = "1") Integer page,
    // @RequestParam(required = false, defaultValue = "20") Integer size) {
    // // Keep your existing filtering (no structure change)
    // List<StudentRegistry> all = filteredRows(cohortNo, groupNo, active, claimed,
    // q); // :contentReference[oaicite:1]{index=1}

    // // ---- pagination (1-based page from UI) ----
    // int safeSize = (size == null || size < 5) ? 20 : Math.min(size, 200);
    // int safePage = (page == null || page < 1) ? 1 : page;

    // int total = all.size();
    // int totalPages = (int) Math.ceil(total / (double) safeSize);

    // if (totalPages > 0 && safePage > totalPages)
    // safePage = totalPages;

    // int fromIndex = (safePage - 1) * safeSize;
    // int toIndex = Math.min(fromIndex + safeSize, total);
    // List<StudentRegistry> rows = (fromIndex >= total)
    // ? Collections.emptyList()
    // : all.subList(fromIndex, toIndex);

    // model.addAttribute("rows", rows);
    // model.addAttribute("cohorts", cohortRepo.findAllByOrderByCohortNoAsc());
    // model.addAttribute("shifts", ShiftTime.values());
    // model.addAttribute("filterCohortNo", cohortNo);
    // model.addAttribute("filterGroupNo", groupNo);
    // model.addAttribute("filterActive", active);
    // model.addAttribute("filterClaimed", claimed);
    // model.addAttribute("q", q);
    // model.addAttribute("msg", msg);

    // // NEW pagination model attributes
    // model.addAttribute("page", safePage);
    // model.addAttribute("size", safeSize);
    // model.addAttribute("totalRows", total);
    // model.addAttribute("totalPages", totalPages);
    // return "admin/student_registry";
    // }

    // -------------------------
    // ✅ NEW: SYNC registry -> users (create missing UserAccount)
    // -------------------------
    @PostMapping("/sync-users")
    public String syncUsers(@RequestParam(required = false, defaultValue = "true") boolean onlyActive) {

        List<StudentRegistry> all = registryRepo.findAll();

        int created = 0;
        int skipped = 0;

        for (StudentRegistry r : all) {
            if (r == null)
                continue;

            if (onlyActive && !r.isActive()) {
                skipped++;
                continue;
            }

            // if already linked, skip
            if (r.getUser() != null) {
                skipped++;
                continue;
            }

            String login = (r.getStudentLogin() == null) ? "" : r.getStudentLogin().trim();
            if (login.isBlank()) {
                skipped++;
                continue;
            }

            // If user exists but registry not linked, link it
            UserAccount existing = userRepo.findByUsername(login).orElse(null);
            if (existing != null) {
                r.setUser(existing);
                r.setClaimed(true);
                if (r.getClaimedAt() == null)
                    r.setClaimedAt(LocalDateTime.now(ZONE));
                registryRepo.save(r);
                skipped++;
                continue;
            }

            // create new user
            UserAccount u = new UserAccount();
            u.setUsername(login);

            // ✅ default password = studentLogin (you can change later)
            u.setPasswordHash(passwordEncoder.encode(login));
            u.setRole(Role.STUDENT);
            u.setEnabled(true);
            u.setMustChangePassword(true);

            // sync timetable and personal info fields
            u.setCohort(r.getCohort());
            u.setGroupNo(r.getGroupNo());
            u.setClassName(r.getClassName());
            u.setShiftTime(r.getShiftTime());
            u.setFullName(r.getFullName());
            u.setEmail(r.getEmail());
            u.setPhone(r.getPhone());

            userRepo.save(u);

            // link registry to user + mark claimed
            r.setUser(u);
            r.setClaimed(true);
            r.setClaimedAt(LocalDateTime.now(ZONE));
            registryRepo.save(r);

            created++;
        }

        return "redirect:/admin/students/registry?msg=sync_users_created_" + created + "_skipped_" + skipped;
    }

    // -------------------------
    // EXPORT
    // -------------------------
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportCsv(@RequestParam(required = false) Integer cohortNo,
            @RequestParam(required = false) Integer groupNo,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Boolean claimed,
            @RequestParam(required = false) String q) {

        List<StudentRegistry> rows = filteredRows(cohortNo, groupNo, active, claimed, q, null);

        String bom = "\uFEFF";
        StringBuilder sb = new StringBuilder();
        sb.append("studentLogin,lastNameKh,firstNameKh,firstName,lastName,gender,dateOfBirth,phone,email,remark,cohortNo,groupNo,className,shiftTime\n");

        for (StudentRegistry r : rows) {
            String studentLogin = safe(r.getStudentLogin());
            String lastNameKh = safe(r.getLastNameKh());
            String firstNameKh = safe(r.getFirstNameKh());
            String firstName = safe(r.getFirstName());
            String lastName = safe(r.getLastName());
            String gender = safe(r.getGender());
            String dob = (r.getDateOfBirth() != null) ? r.getDateOfBirth().toString() : "";
            String phone = safe(r.getPhone());
            String email = safe(r.getEmail());
            String remark = safe(r.getRemark());
            String cohort = (r.getCohort() != null && r.getCohort().getCohortNo() != null)
                    ? r.getCohort().getCohortNo().toString()
                    : "";
            String group = (r.getGroupNo() != null) ? r.getGroupNo().toString() : "";
            String className = safe(r.getClassName());
            String shift = (r.getShiftTime() != null) ? r.getShiftTime().name() : "";

            sb.append(csvEscape(studentLogin)).append(",")
                    .append(csvEscape(lastNameKh)).append(",")
                    .append(csvEscape(firstNameKh)).append(",")
                    .append(csvEscape(firstName)).append(",")
                    .append(csvEscape(lastName)).append(",")
                    .append(csvEscape(gender)).append(",")
                    .append(csvEscape(dob)).append(",")
                    .append(csvEscape(phone)).append(",")
                    .append(csvEscape(email)).append(",")
                    .append(csvEscape(remark)).append(",")
                    .append(csvEscape(cohort)).append(",")
                    .append(csvEscape(group)).append(",")
                    .append(csvEscape(className)).append(",")
                    .append(csvEscape(shift))
                    .append("\n");
        }

        String filename = "student_registry_export_" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body((bom + sb).getBytes(StandardCharsets.UTF_8));
    }

    private List<StudentRegistry> filteredRows(Integer cohortNo, Integer groupNo, Boolean active, Boolean claimed,
            String q, String faculty) {
        List<StudentRegistry> rows = registryRepo.findAll();

        if (cohortNo != null) {
            rows = rows.stream()
                    .filter(r -> r.getCohort() != null && Objects.equals(r.getCohort().getCohortNo(), cohortNo))
                    .collect(Collectors.toList());
        }
        if (groupNo != null) {
            rows = rows.stream()
                    .filter(r -> Objects.equals(r.getGroupNo(), groupNo))
                    .collect(Collectors.toList());
        }
        if (active != null) {
            rows = rows.stream()
                    .filter(r -> r.isActive() == active)
                    .collect(Collectors.toList());
        }
        if (claimed != null) {
            rows = rows.stream()
                    .filter(r -> r.isClaimed() == claimed)
                    .collect(Collectors.toList());
        }
        if (q != null && !q.isBlank()) {
            String needle = q.trim().toLowerCase();
            rows = rows.stream()
                    .filter(r -> (r.getStudentLogin() != null && r.getStudentLogin().toLowerCase().contains(needle)) ||
                            (r.getFullName() != null && r.getFullName().toLowerCase().contains(needle)) ||
                            (r.getFirstName() != null && r.getFirstName().toLowerCase().contains(needle)) ||
                            (r.getLastName() != null && r.getLastName().toLowerCase().contains(needle)) ||
                            (r.getEmail() != null && r.getEmail().toLowerCase().contains(needle)) ||
                            (r.getClassName() != null && r.getClassName().toLowerCase().contains(needle)))
                    .collect(Collectors.toList());
        }
        if (faculty != null && !faculty.isBlank()) {
            rows = rows.stream()
                    .filter(r -> r.getCohort() != null && faculty.equals(r.getCohort().getFaculty()))
                    .collect(Collectors.toList());
        }

        rows.sort(Comparator.comparing(StudentRegistry::getId, Comparator.nullsLast(Long::compareTo)).reversed());
        return rows;
    }

    // -------------------------
    // IMPORT (updated: also create UserAccount automatically)
    // -------------------------
    @PostMapping("/import")
    public String importCsv(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return "redirect:/admin/students/registry?msg=empty_file";
        }

        int imported = 0;
        int updated = 0;
        int groupChanged = 0;
        int skipped = 0;
        int errors = 0;
        int createdUsers = 0;

        int errCols = 0, errMissing = 0, errCohort = 0, errShift = 0;

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            String firstLine = br.readLine();
            if (firstLine == null) {
                return "redirect:/admin/students/registry?msg=empty_file";
            }

            char delim = detectDelimiter(firstLine);

            List<String> firstFields = parseCsvLine(firstLine, delim);
            Map<String, Integer> headerIdx = headerIndex(firstFields);

            boolean hasHeader = looksLikeHeader(firstFields, headerIdx);

            if (!hasHeader) {
                headerIdx = defaultIndex(firstFields.size());
                ProcessResult pr = processRow(firstFields, headerIdx);
                if (pr.code == 1) { imported++; createdUsers += pr.createdUser ? 1 : 0; }
                else if (pr.code == 2) { updated++; if (pr.groupChanged) groupChanged++; }
                else if (pr.code == 0) skipped++;
                else {
                    errors++;
                    if (pr.code == -2) errCols++;
                    if (pr.code == -3) errMissing++;
                    if (pr.code == -4) errCohort++;
                    if (pr.code == -5) errShift++;
                }
            }

            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                List<String> fields = parseCsvLine(line, delim);
                ProcessResult pr = processRow(fields, headerIdx);
                if (pr.code == 1) { imported++; createdUsers += pr.createdUser ? 1 : 0; }
                else if (pr.code == 2) { updated++; if (pr.groupChanged) groupChanged++; }
                else if (pr.code == 0) skipped++;
                else {
                    errors++;
                    if (pr.code == -2) errCols++;
                    if (pr.code == -3) errMissing++;
                    if (pr.code == -4) errCohort++;
                    if (pr.code == -5) errShift++;
                }
            }

        } catch (Exception e) {
            return "redirect:/admin/students/registry?msg=import_failed";
        }

        String msg = "imported_" + imported +
                "_updated_" + updated +
                "_groupchanged_" + groupChanged +
                "_skipped_" + skipped +
                "_errors_" + errors +
                "_users_" + createdUsers;

        if (errors > 0) {
            msg += "_badcols_" + errCols +
                    "_missing_" + errMissing +
                    "_badcohort_" + errCohort +
                    "_badshift_" + errShift;
        }

        return "redirect:/admin/students/registry?msg=" + msg;
    }

    private static class ProcessResult {
        final int code; // 1 imported, 2 updated, 0 skipped, negative error
        final boolean createdUser;
        final boolean groupChanged;

        ProcessResult(int code, boolean createdUser, boolean groupChanged) {
            this.code = code;
            this.createdUser = createdUser;
            this.groupChanged = groupChanged;
        }
    }

    /**
     * return codes:
     * 1 = imported
     * 0 = skipped (already exists)
     * -2 = invalid columns count
     * -3 = missing required fields
     * -4 = invalid cohort
     * -5 = invalid shift
     */
    private ProcessResult processRow(List<String> fields, Map<String, Integer> idx) {

        String studentLogin = stripBom(get(fields, idx.get("studentLogin"))).trim();
        String fullName = get(fields, idx.get("fullname")).trim();
        String firstName = get(fields, idx.get("firstname")).trim();
        String lastName = get(fields, idx.get("lastname")).trim();
        String firstNameKh = get(fields, idx.get("firstnamekh")).trim();
        String lastNameKh = get(fields, idx.get("lastnamekh")).trim();
        String remark = get(fields, idx.get("remark")).trim();
        String genderRaw = get(fields, idx.get("gender")).trim();
        String dobRaw = get(fields, idx.get("dateofbirth")).trim();
        String phone = get(fields, idx.get("phone")).trim();
        String email = get(fields, idx.get("email")).trim();
        Integer cohortNo = parseInt(get(fields, idx.get("cohortno")));
        Integer groupNo = parseInt(get(fields, idx.get("groupno")));
        String className = get(fields, idx.get("classname")).trim();
        String shiftRaw = get(fields, idx.get("shifttime")).trim();

        if (fields.size() < 1)
            return new ProcessResult(-2, false, false);

        if (studentLogin.isBlank())
            return new ProcessResult(-3, false, false);

        // Cohort is optional — only validate if provided
        Cohort cohort = null;
        if (cohortNo != null) {
            cohort = cohortRepo.findByCohortNo(cohortNo).orElse(null);
            if (cohort == null)
                return new ProcessResult(-4, false, false);
        }

        // Shift is optional — only validate if provided
        ShiftTime shift = null;
        if (!shiftRaw.isBlank()) {
            try {
                shift = ShiftTime.valueOf(normalizeEnum(shiftRaw));
            } catch (Exception ex) {
                return new ProcessResult(-5, false, false);
            }
        }

        // Derive fullName from firstName+lastName if not given directly
        String resolvedFullName = fullName.isBlank()
                ? (firstName.isBlank() && lastName.isBlank() ? null : (firstName + " " + lastName).trim())
                : fullName;

        // Normalize gender
        String gender = normalizeGender(genderRaw);

        // Parse date of birth
        LocalDate dob = parseDate(dobRaw);

        // Update existing record instead of skipping
        StudentRegistry existing = registryRepo.findByStudentLoginIgnoreCase(studentLogin).orElse(null);
        if (existing != null) {
            // Detect group or cohort change before applying updates
            boolean groupChanged =
                (groupNo != null && !groupNo.equals(existing.getGroupNo())) ||
                (cohort != null && (existing.getCohort() == null ||
                    !cohort.getId().equals(existing.getCohort().getId())));

            if (resolvedFullName != null) existing.setFullName(resolvedFullName);
            if (!firstName.isBlank()) existing.setFirstName(firstName);
            if (!lastName.isBlank()) existing.setLastName(lastName);
            if (!firstNameKh.isBlank()) existing.setFirstNameKh(firstNameKh);
            if (!lastNameKh.isBlank()) existing.setLastNameKh(lastNameKh);
            if (!genderRaw.isBlank()) existing.setGender(gender);
            if (dob != null) existing.setDateOfBirth(dob);
            if (!phone.isBlank()) existing.setPhone(phone);
            if (!email.isBlank()) existing.setEmail(email);
            if (!remark.isBlank()) existing.setRemark(remark);
            if (cohort != null) existing.setCohort(cohort);
            if (groupNo != null) existing.setGroupNo(groupNo);
            if (!className.isBlank()) existing.setClassName(className);
            if (shift != null) existing.setShiftTime(shift);
            registryRepo.save(existing);
            syncRegistryToUser(existing);
            return new ProcessResult(2, false, groupChanged);
        }

        StudentRegistry r = new StudentRegistry();
        r.setStudentLogin(studentLogin);
        r.setFullName(resolvedFullName);
        r.setFirstName(firstName.isBlank() ? null : firstName);
        r.setLastName(lastName.isBlank() ? null : lastName);
        r.setFirstNameKh(firstNameKh.isBlank() ? null : firstNameKh);
        r.setLastNameKh(lastNameKh.isBlank() ? null : lastNameKh);
        r.setRemark(remark.isBlank() ? null : remark);
        r.setGender(gender);
        r.setDateOfBirth(dob);
        r.setPhone(phone.isBlank() ? null : phone);
        r.setEmail(email.isBlank() ? null : email);
        r.setCohort(cohort);
        r.setGroupNo(groupNo);
        r.setClassName(className.isBlank() ? null : className);
        r.setShiftTime(shift);
        r.setActive(true);
        r.setClaimed(false);
        r.setImportedAt(LocalDateTime.now(ZONE));

        registryRepo.save(r);

        boolean createdUser = ensureUserForRegistry(r);
        return new ProcessResult(1, createdUser, false);
    }

    private static String normalizeGender(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim().toUpperCase(Locale.ROOT);
        if (s.equals("M") || s.equals("MALE") || s.equals("ប្រុស") || s.equals("PROUS")) return "MALE";
        if (s.equals("F") || s.equals("FEMALE") || s.equals("ស្រី") || s.equals("SREY")) return "FEMALE";
        return null;
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String[] patterns = {"M/d/yyyy", "MM/dd/yyyy", "yyyy-MM-dd", "dd/MM/yyyy", "d/M/yyyy"};
        for (String p : patterns) {
            try {
                return LocalDate.parse(raw.trim(), DateTimeFormatter.ofPattern(p));
            } catch (Exception ignored) {}
        }
        return null;
    }

    private String renderEditError(Model model, StudentRegistry record, StudentRegistryEditForm form, String error) {
        model.addAttribute("form", form);
        model.addAttribute("error", error);
        populateEditModel(model, record);
        return "admin/student_registry_edit";
    }

    private void populateEditModel(Model model, StudentRegistry record) {
        model.addAttribute("record", record);
        model.addAttribute("cohorts", cohortRepo.findAllByOrderByCohortNoAsc());
        model.addAttribute("shifts", ShiftTime.values());
    }

    private void syncRegistryUserAccount(StudentRegistry record) {
        if (record == null || record.getUser() == null) {
            return;
        }

        UserAccount linkedUser = record.getUser();
        linkedUser.setUsername(record.getStudentLogin());
        linkedUser.setFullName(record.getFullName());
        linkedUser.setEmail(record.getEmail());
        linkedUser.setPhone(record.getPhone());
        linkedUser.setGender(record.getGender());
        linkedUser.setCohort(record.getCohort());
        linkedUser.setGroupNo(record.getGroupNo());
        linkedUser.setClassName(record.getClassName());
        linkedUser.setShiftTime(record.getShiftTime());
        userRepo.save(linkedUser);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Create UserAccount for registry if missing, link it, mark claimed.
     * Default password = studentLogin
     */
    private void syncRegistryToUser(StudentRegistry r) {
        if (r == null || r.getUser() == null) return;
        UserAccount u = r.getUser();
        if (r.getFullName() != null) u.setFullName(r.getFullName());
        if (r.getEmail() != null) u.setEmail(r.getEmail());
        if (r.getPhone() != null) u.setPhone(r.getPhone());
        if (r.getCohort() != null) u.setCohort(r.getCohort());
        if (r.getGroupNo() != null) u.setGroupNo(r.getGroupNo());
        if (r.getClassName() != null) u.setClassName(r.getClassName());
        if (r.getShiftTime() != null) u.setShiftTime(r.getShiftTime());
        userRepo.save(u);
    }

    private boolean ensureUserForRegistry(StudentRegistry r) {
        if (r == null)
            return false;
        if (r.getUser() != null)
            return false;

        String login = (r.getStudentLogin() == null) ? "" : r.getStudentLogin().trim();
        if (login.isBlank())
            return false;

        UserAccount existing = userRepo.findByUsername(login).orElse(null);
        if (existing != null) {
            r.setUser(existing);
            r.setClaimed(true);
            if (r.getClaimedAt() == null)
                r.setClaimedAt(LocalDateTime.now(ZONE));
            registryRepo.save(r);
            return false;
        }

        UserAccount u = new UserAccount();
        u.setUsername(login);
        u.setPasswordHash(passwordEncoder.encode(login));
        u.setRole(Role.STUDENT);

        u.setCohort(r.getCohort());
        u.setGroupNo(r.getGroupNo());
        u.setClassName(r.getClassName());
        u.setShiftTime(r.getShiftTime());

        // Sync personal info fields
        u.setFullName(r.getFullName());
        u.setEmail(r.getEmail());
        u.setPhone(r.getPhone());

        u.setEnabled(false);
        userRepo.save(u);

        r.setUser(u);
        r.setClaimed(false);
        r.setClaimedAt(null);
        registryRepo.save(r);

        return true;
    }

    @PostMapping("/{id}/toggle")
    public String toggle(@PathVariable Long id) {
        StudentRegistry r = registryRepo.findById(id).orElseThrow();
        r.setActive(!r.isActive());
        registryRepo.save(r);
        return "redirect:/admin/students/registry?msg=toggled";
    }

    @PostMapping("/{id}/reset-password")
    public String resetPasswordToDefault(@PathVariable Long id) {
        StudentRegistry record = registryRepo.findById(id).orElseThrow();

        if (record.getUser() == null) {
            return "redirect:/admin/students/registry?msg=password_reset_unavailable";
        }

        String defaultPassword = trimToNull(record.getStudentLogin());
        if (defaultPassword == null) {
            return "redirect:/admin/students/registry?msg=password_reset_unavailable";
        }

        UserAccount linkedUser = record.getUser();
        linkedUser.setPasswordHash(passwordEncoder.encode(defaultPassword));
        linkedUser.setMustChangePassword(true);
        userRepo.save(linkedUser);

        return "redirect:/admin/students/registry?msg=password_reset_default";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        StudentRegistry r = registryRepo.findById(id).orElseThrow();

        if (r.isClaimed()) {
            r.setActive(false);
            registryRepo.save(r);
            return "redirect:/admin/students/registry?msg=deactivated";
        }

        registryRepo.delete(r);
        return "redirect:/admin/students/registry?msg=deleted";
    }

    // -------------------------
    // TEMPLATE
    // -------------------------
    @GetMapping("/template")
    public ResponseEntity<byte[]> downloadTemplate() {
        String bom = "\uFEFF";
        String nl = "\r\n";

        var cohorts = cohortRepo.findAllByOrderByCohortNoAsc();
        String c1 = cohorts.isEmpty() || cohorts.get(0).getCohortNo() == null ? ""
                : cohorts.get(0).getCohortNo().toString();
        String c2 = (cohorts.size() > 1 && cohorts.get(1).getCohortNo() != null)
                ? cohorts.get(1).getCohortNo().toString()
                : c1;

        String csv = "No,studentLogin,lastNameKh,firstNameKh,firstName,lastName,gender,dateOfBirth,phone,email,remark,cohortNo,groupNo,className,shiftTime" + nl +
                "1,NUMP330612659,គីម,វង្សវ្យថា,KIM,VUTHYVATHA,MALE,2005-06-15,77494842,vathanakim14@gmail.com,," + c1 + ",1,13A,MORNING" + nl +
                "2,NUMP330611247,ចាយ,ចន្ទា,CHAY,CHANTHA,FEMALE,2006-12-18,975915907,tha754688@gmail.com,," + c1 + ",1,13A,MORNING" + nl +
                "3,NUMP330611927,ជា,លីហ៊ែង,CHEA,LYHENG,MALE,2007-03-30,15603678,chealyheng384@gmail.com,," + c2 + ",2,13B,AFTERNOON" + nl +
                "4,NUMP330613240,ញ៉ែប,សុវណ្ណារី,NHEB,SOVANNARY,FEMALE,2007-01-02,968526154,Sovannaryzinll@gmail.com,," + c2 + ",2,13B,EARLY_AFTERNOON" + nl;

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"student_registry_template.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body((bom + csv).getBytes(StandardCharsets.UTF_8));
    }

    // -------------------------
    // Helpers
    // -------------------------

    private Integer parseInt(String s) {
        try {
            if (s == null)
                return null;
            String t = s.trim();
            if (t.isBlank())
                return null;
            return Integer.parseInt(t);
        } catch (Exception e) {
            return null;
        }
    }

    private static String safe(String s) {
        return (s == null) ? "" : s;
    }

    private static String csvEscape(String v) {
        if (v == null)
            return "";
        boolean needQuotes = v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r");
        String x = v.replace("\"", "\"\"");
        return needQuotes ? ("\"" + x + "\"") : x;
    }

    private static String stripBom(String s) {
        if (s == null)
            return "";
        return s.replace("\uFEFF", "");
    }

    private static char detectDelimiter(String line) {
        if (line == null)
            return ',';
        int commas = countChar(line, ',');
        int semis = countChar(line, ';');
        int tabs = countChar(line, '\t');
        if (semis > commas && semis >= tabs)
            return ';';
        if (tabs > commas && tabs > semis)
            return '\t';
        return ',';
    }

    private static int countChar(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++)
            if (s.charAt(i) == c)
                n++;
        return n;
    }

    private static List<String> parseCsvLine(String line, char delim) {
        List<String> out = new ArrayList<>();
        if (line == null)
            return out;

        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);

            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
                continue;
            }

            if (!inQuotes && ch == delim) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        out.add(cur.toString());
        return out;
    }

    private static boolean looksLikeHeader(List<String> fields, Map<String, Integer> idx) {
        // Accept header if studentLogin column is present (cohort/group/shift are optional)
        return idx.containsKey("studentLogin")
                || (idx.containsKey("firstname") && idx.containsKey("lastname"));
    }

    private static Map<String, Integer> defaultIndex(int fieldCount) {
        Map<String, Integer> m = new HashMap<>();
        m.put("studentLogin", 0);
        m.put("fullname", 1);
        m.put("cohortno", 2);
        m.put("groupno", 3);

        if (fieldCount >= 6) {
            m.put("classname", 4);
            m.put("shifttime", 5);
        } else {
            m.put("classname", -1);
            m.put("shifttime", 4);
        }
        return m;
    }

    private static String get(List<String> fields, Integer i) {
        if (i == null || i < 0)
            return "";
        if (fields == null)
            return "";
        if (i >= fields.size())
            return "";
        String v = fields.get(i);
        return (v == null) ? "" : v;
    }

    private static String normalizeEnum(String raw) {
        if (raw == null)
            return "";
        String s = raw.trim().toUpperCase(Locale.ROOT);
        s = s.replace("-", "_").replace(" ", "_");
        while (s.contains("__"))
            s = s.replace("__", "_");
        return s;
    }

    public static class StudentRegistryEditForm {
        private String studentLogin;
        private String fullName;
        private String firstName;
        private String lastName;
        private String firstNameKh;
        private String lastNameKh;
        private String gender;
        private String dateOfBirth;
        private String phone;
        private String email;
        private String remark;
        private Long cohortId;
        private Integer groupNo;
        private String className;
        private String shiftTime;

        static StudentRegistryEditForm from(StudentRegistry record) {
            StudentRegistryEditForm form = new StudentRegistryEditForm();
            form.setStudentLogin(record.getStudentLogin());
            form.setFullName(record.getFullName());
            form.setFirstName(record.getFirstName());
            form.setLastName(record.getLastName());
            form.setFirstNameKh(record.getFirstNameKh());
            form.setLastNameKh(record.getLastNameKh());
            form.setGender(record.getGender());
            form.setDateOfBirth(record.getDateOfBirth() != null ? record.getDateOfBirth().toString() : null);
            form.setPhone(record.getPhone());
            form.setEmail(record.getEmail());
            form.setRemark(record.getRemark());
            form.setCohortId(record.getCohort() != null ? record.getCohort().getId() : null);
            form.setGroupNo(record.getGroupNo());
            form.setClassName(record.getClassName());
            form.setShiftTime(record.getShiftTime() != null ? record.getShiftTime().name() : null);
            return form;
        }

        public String getStudentLogin() {
            return studentLogin;
        }

        public void setStudentLogin(String studentLogin) {
            this.studentLogin = studentLogin;
        }

        public String getFullName() {
            return fullName;
        }

        public void setFullName(String fullName) {
            this.fullName = fullName;
        }

        public String getFirstName() {
            return firstName;
        }

        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public void setLastName(String lastName) {
            this.lastName = lastName;
        }

        public String getFirstNameKh() {
            return firstNameKh;
        }

        public void setFirstNameKh(String firstNameKh) {
            this.firstNameKh = firstNameKh;
        }

        public String getLastNameKh() {
            return lastNameKh;
        }

        public void setLastNameKh(String lastNameKh) {
            this.lastNameKh = lastNameKh;
        }

        public String getGender() {
            return gender;
        }

        public void setGender(String gender) {
            this.gender = gender;
        }

        public String getDateOfBirth() {
            return dateOfBirth;
        }

        public void setDateOfBirth(String dateOfBirth) {
            this.dateOfBirth = dateOfBirth;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getRemark() {
            return remark;
        }

        public void setRemark(String remark) {
            this.remark = remark;
        }

        public Long getCohortId() {
            return cohortId;
        }

        public void setCohortId(Long cohortId) {
            this.cohortId = cohortId;
        }

        public Integer getGroupNo() {
            return groupNo;
        }

        public void setGroupNo(Integer groupNo) {
            this.groupNo = groupNo;
        }

        public String getClassName() {
            return className;
        }

        public void setClassName(String className) {
            this.className = className;
        }

        public String getShiftTime() {
            return shiftTime;
        }

        public void setShiftTime(String shiftTime) {
            this.shiftTime = shiftTime;
        }
    }

    private static Map<String, Integer> headerIndex(List<String> headerFields) {
        Map<String, Integer> m = new HashMap<>();
        if (headerFields == null)
            return m;

        for (int i = 0; i < headerFields.size(); i++) {
            String h = stripBom(headerFields.get(i));
            String key = normalizeKey(h);

            if (key.equals("studentlogin") || key.equals("studentnumber") || key.equals("student_id")
                    || key.equals("studentid") || key.equals("lekskal") || key.equals("no")
                    || key.equals("number") || key.equals("id")) {
                m.put("studentLogin", i);
            } else if (key.equals("fullname") || key.equals("studentname") || key.equals("name")
                    || key.equals("full_name")) {
                m.put("fullname", i);
            } else if (key.equals("firstname") || key.equals("first_name")) {
                m.put("firstname", i);
            } else if (key.equals("lastname") || key.equals("last_name") || key.equals("surname")
                    || key.equals("familyname")) {
                m.put("lastname", i);
            } else if (key.equals("firstnamekh") || key.equals("first_name_kh") || key.equals("namkluon")
                    || key.equals("namekloun") || key.equals("namkloun")) {
                m.put("firstnamekh", i);
            } else if (key.equals("lastnamekh") || key.equals("last_name_kh") || key.equals("namtrakoul")
                    || key.equals("namtrakul")) {
                m.put("lastnamekh", i);
            } else if (key.equals("remark") || key.equals("note") || key.equals("samkal")
                    || key.equals("status") || key.equals("note")) {
                m.put("remark", i);
            } else if (key.equals("gender") || key.equals("sex") || key.equals("phet")) {
                m.put("gender", i);
            } else if (key.equals("dateofbirth") || key.equals("dob") || key.equals("birthdate")
                    || key.equals("date_of_birth") || key.equals("birthday")) {
                m.put("dateofbirth", i);
            } else if (key.equals("phone") || key.equals("telephone") || key.equals("mobile")
                    || key.equals("phonenumber") || key.equals("tursaph")) {
                m.put("phone", i);
            } else if (key.equals("email") || key.equals("emailaddress") || key.equals("email_address")) {
                m.put("email", i);
            } else if (key.equals("cohortno") || key.equals("cohort") || key.equals("batch")) {
                m.put("cohortno", i);
            } else if (key.equals("groupno") || key.equals("group") || key.equals("groupnumber")) {
                m.put("groupno", i);
            } else if (key.equals("classname") || key.equals("class") || key.equals("class_name")) {
                m.put("classname", i);
            } else if (key.equals("shifttime") || key.equals("shift") || key.equals("shift_time")
                    || key.equals("session")) {
                m.put("shifttime", i);
            }
        }
        return m;
    }

    private static String normalizeKey(String s) {
        if (s == null)
            return "";
        String x = s.trim().toLowerCase(Locale.ROOT);
        x = x.replace("\uFEFF", "");
        x = x.replace(" ", "");
        x = x.replace("-", "");
        x = x.replace("_", "");
        return x;
    }
}