package kh.edu.num.feedback.web.admin;

import kh.edu.num.feedback.domain.entity.Enrollment;
import kh.edu.num.feedback.domain.entity.Role;
import kh.edu.num.feedback.domain.repo.ClassSectionRepository;
import kh.edu.num.feedback.domain.repo.EnrollmentRepository;
import kh.edu.num.feedback.domain.repo.UserAccountRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.transaction.Transactional;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Controller
@RequestMapping("/admin/enrollments")
public class AdminEnrollmentController {

  private final EnrollmentRepository enrollRepo;
  private final ClassSectionRepository sectionRepo;
  private final UserAccountRepository userRepo;

  public AdminEnrollmentController(EnrollmentRepository enrollRepo,
      ClassSectionRepository sectionRepo,
      UserAccountRepository userRepo) {
    this.enrollRepo = enrollRepo;
    this.sectionRepo = sectionRepo;
    this.userRepo = userRepo;
  }

  @GetMapping
  public String list(Model model,
      @RequestParam(required = false) String msg,
      @RequestParam(required = false) Long studentId,
      @RequestParam(required = false, defaultValue = "1")  Integer page,
      @RequestParam(required = false, defaultValue = "20") Integer size) {

    List<Enrollment> all = (studentId != null)
        ? enrollRepo.findByStudentId(studentId)
        : enrollRepo.findAll();

    all.sort(Comparator
        .comparing((Enrollment e) -> e.getStudent() != null ? e.getStudent().getUsername() : "")
        .thenComparing(e -> e.getSection() != null && e.getSection().getSemester() != null
            ? e.getSection().getSemester().getName() : "")
        .thenComparing(e -> e.getSection() != null && e.getSection().getCourse() != null
            ? e.getSection().getCourse().getCode() : ""));

    // pagination
    int safeSize = (size == null || size < 1) ? 20 : size;
    int safePage = (page == null || page < 1) ? 1 : page;
    int totalRows  = all.size();
    int totalPages = (int) Math.ceil(totalRows / (double) safeSize);
    if (totalPages > 0 && safePage > totalPages) safePage = totalPages;
    int from = (safePage - 1) * safeSize;
    int to   = Math.min(from + safeSize, totalRows);
    List<Enrollment> pageRows = totalRows == 0 ? Collections.emptyList() : all.subList(from, to);

    var sections = sectionRepo.findAll();
    sections.sort(Comparator
        .comparing((kh.edu.num.feedback.domain.entity.ClassSection s) ->
            s.getSemester() != null ? s.getSemester().getName() : "")
        .thenComparing(s -> s.getCohort() != null ? String.valueOf(s.getCohort().getId()) : "")
        .thenComparing(s -> s.getGroupNo() != null ? s.getGroupNo() : 0)
        .thenComparing(s -> s.getShiftTime() != null ? s.getShiftTime().name() : "")
        .thenComparing(s -> s.getBuilding() != null ? s.getBuilding() : "")
        .thenComparing(s -> s.getRoom() != null ? s.getRoom() : ""));

    model.addAttribute("sections", sections);
    model.addAttribute("enrollments", pageRows);
    model.addAttribute("students", userRepo.findByRole(Role.STUDENT));
    model.addAttribute("selectedStudentId", studentId);
    model.addAttribute("msg", msg);

    // pagination attrs
    model.addAttribute("page", safePage);
    model.addAttribute("size", safeSize);
    model.addAttribute("totalRows", totalRows);
    model.addAttribute("totalPages", totalPages);

    // KPI stats
    long totalEnrollments = enrollRepo.count();
    long enrolledStudents = enrollRepo.findAll().stream()
        .map(e -> e.getStudent() != null ? e.getStudent().getId() : null)
        .filter(Objects::nonNull).distinct().count();
    model.addAttribute("totalEnrollments", totalEnrollments);
    model.addAttribute("enrolledStudents", enrolledStudents);
    model.addAttribute("totalSections", sectionRepo.count());
    model.addAttribute("totalStudents", userRepo.findByRole(Role.STUDENT).size());

    return "admin/enrollments";
  }

  @PostMapping
  public String create(@RequestParam Long sectionId,
      @RequestParam Long studentId) {

    var section = sectionRepo.findById(sectionId).orElseThrow();
    var student = userRepo.findById(studentId).orElseThrow();

    // Avoid relying on exception/rollback for duplicates
    if (enrollRepo.existsByStudent_IdAndSection_Id(student.getId(), section.getId())) {
      return "redirect:/admin/enrollments?msg=duplicate";
    }

    Enrollment e = new Enrollment();
    e.setSection(section);
    e.setStudent(student);
    enrollRepo.save(e);
    return "redirect:/admin/enrollments?msg=created";
  }

  /**
   * Bulk matrix enrollment:
   * - students (many) × sections (many)
   * - creates all combinations that don't already exist
   *
   * NOTE: Your enrollments.html submits "studentIds" and "sectionIds".
   */
  @Transactional
  @PostMapping("/bulk")
  public String bulkMatrixCreate(
      @RequestParam List<Long> studentIds,
      @RequestParam List<Long> sectionIds,
      RedirectAttributes ra) {

    int created = 0;
    int dup = 0;

    // Load in batch for better performance
    var students = userRepo.findAllById(studentIds);
    var sections = sectionRepo.findAllById(sectionIds);

    for (var st : students) {
      if (st == null)
        continue;
      for (var sec : sections) {
        if (sec == null)
          continue;

        if (enrollRepo.existsByStudent_IdAndSection_Id(st.getId(), sec.getId())) {
          dup++;
          continue;
        }

        Enrollment e = new Enrollment();
        e.setStudent(st);
        e.setSection(sec);
        enrollRepo.save(e);
        created++;
      }
    }

    // addAttribute -> query string, so enrollments.html can read
    // param.created/param.dup
    ra.addAttribute("msg", "bulk_created");
    ra.addAttribute("created", created);
    ra.addAttribute("dup", dup);
    return "redirect:/admin/enrollments";
  }

  @PostMapping("/{id}/delete")
  public String delete(@PathVariable Long id) {
    enrollRepo.deleteById(id);
    return "redirect:/admin/enrollments?msg=deleted";
  }
}

// package kh.edu.num.feedback.web.admin;

// import kh.edu.num.feedback.domain.entity.Enrollment;
// import kh.edu.num.feedback.domain.entity.Role;
// import kh.edu.num.feedback.domain.repo.ClassSectionRepository;
// import kh.edu.num.feedback.domain.repo.EnrollmentRepository;
// import kh.edu.num.feedback.domain.repo.UserAccountRepository;
// import org.springframework.dao.DataIntegrityViolationException;
// import org.springframework.stereotype.Controller;
// import org.springframework.ui.Model;
// import org.springframework.web.bind.annotation.*;
// import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// import jakarta.transaction.Transactional;

// import java.util.Comparator;
// import java.util.List;

// @Controller
// @RequestMapping("/admin/enrollments")
// public class AdminEnrollmentController {

// private final EnrollmentRepository enrollRepo;
// private final ClassSectionRepository sectionRepo;
// private final UserAccountRepository userRepo;

// public AdminEnrollmentController(EnrollmentRepository enrollRepo,
// ClassSectionRepository sectionRepo,
// UserAccountRepository userRepo) {
// this.enrollRepo = enrollRepo;
// this.sectionRepo = sectionRepo;
// this.userRepo = userRepo;
// }

// @GetMapping
// public String list(Model model,
// @RequestParam(required = false) String msg,
// @RequestParam(required = false) Long studentId) {

// var enrollments = (studentId != null)
// ? enrollRepo.findByStudentId(studentId)
// : enrollRepo.findAll();

// enrollments.sort(Comparator
// .comparing((Enrollment e) -> e.getStudent() != null ?
// e.getStudent().getUsername() : "")
// .thenComparing(e -> e.getSection() != null && e.getSection().getSemester() !=
// null
// ? e.getSection().getSemester().getName()
// : "")
// .thenComparing(e -> e.getSection() != null && e.getSection().getCourse() !=
// null
// ? e.getSection().getCourse().getCode()
// : ""));

// model.addAttribute("enrollments", enrollments);
// model.addAttribute("sections", sectionRepo.findAll());
// model.addAttribute("students", userRepo.findByRole(Role.STUDENT));
// model.addAttribute("selectedStudentId", studentId);
// model.addAttribute("msg", msg);
// return "admin/enrollments";
// }

// @PostMapping
// public String create(@RequestParam Long sectionId,
// @RequestParam Long studentId) {

// var section = sectionRepo.findById(sectionId).orElseThrow();
// var student = userRepo.findById(studentId).orElseThrow();

// Enrollment e = new Enrollment();
// e.setSection(section);
// e.setStudent(student);

// try {
// enrollRepo.save(e);
// return "redirect:/admin/enrollments?msg=created";
// } catch (DataIntegrityViolationException ex) {
// return "redirect:/admin/enrollments?msg=duplicate";
// }
// }

// @PostMapping("/bulk")
// public String bulkMatrixCreate(
// @RequestParam List<Long> studentIds,
// @RequestParam List<Long> sectionIds,
// RedirectAttributes ra) {
// int created = 0;
// int dup = 0;

// // optional: load in batch for performance
// var students = userRepo.findAllById(studentIds);
// var sections = sectionRepo.findAllById(sectionIds);

// for (var st : students) {
// for (var sec : sections) {

// // prevent duplicates without throwing DB exceptions
// if (enrollRepo.existsByStudent_IdAndSection_Id(st.getId(), sec.getId())) {
// dup++;
// continue;
// }

// Enrollment e = new Enrollment();
// e.setStudent(st);
// e.setSection(sec);

// enrollRepo.save(e);
// created++;
// }
// }

// ra.addAttribute("msg", "bulk_created");
// ra.addAttribute("created", created);
// ra.addAttribute("dup", dup);
// return "redirect:/admin/enrollments";
// }
// // Bulk enroll: select 1 student + many sections
// // @PostMapping("/bulk")
// // public String bulkCreate(@RequestParam Long studentId,
// // @RequestParam List<Long> sectionIds) {

// // var student = userRepo.findById(studentId).orElseThrow();

// // int created = 0;
// // int dup = 0;

// // for (Long sectionId : sectionIds) {
// // if (sectionId == null)
// // continue;
// // var section = sectionRepo.findById(sectionId).orElseThrow();

// // Enrollment e = new Enrollment();
// // e.setStudent(student);
// // e.setSection(section);

// // try {
// // enrollRepo.save(e);
// // created++;
// // } catch (DataIntegrityViolationException ex) {
// // dup++;
// // }
// // }

// // return "redirect:/admin/enrollments?msg=bulk_created&created=" + created +
// // "&dup=" + dup;
// // }

// @PostMapping("/{id}/delete")
// public String delete(@PathVariable Long id) {
// enrollRepo.deleteById(id);
// return "redirect:/admin/enrollments?msg=deleted";
// }
// }
