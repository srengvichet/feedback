package kh.edu.num.feedback.web.student;

import kh.edu.num.feedback.domain.entity.*;
import kh.edu.num.feedback.domain.repo.*;
import kh.edu.num.feedback.service.StudentRegistrySyncService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

@Controller
@RequestMapping("/student/join")
public class StudentJoinController {

  private final ClassJoinCodeRepository joinRepo;
  private final TeachingScheduleRepository teachingRepo;
  private final ClassSectionRepository sectionRepo;
  private final EnrollmentRepository enrollRepo;
  private final UserAccountRepository userRepo;
  private final StudentRegistrySyncService registrySync;

  private static final ZoneId ZONE = ZoneId.of("Asia/Phnom_Penh");

  public StudentJoinController(ClassJoinCodeRepository joinRepo,
                               TeachingScheduleRepository teachingRepo,
                               ClassSectionRepository sectionRepo,
                               EnrollmentRepository enrollRepo,
                               UserAccountRepository userRepo,
                               StudentRegistrySyncService registrySync) {
    this.joinRepo = joinRepo;
    this.teachingRepo = teachingRepo;
    this.sectionRepo = sectionRepo;
    this.enrollRepo = enrollRepo;
    this.userRepo = userRepo;
    this.registrySync = registrySync;
  }

  @GetMapping("/{code}")
  public String preview(@PathVariable String code, Authentication auth, Model model) {
    String cleanCode = normalizeCode(code);
    var now = LocalDateTime.now(ZONE);

    if (auth == null) return "redirect:/login";

    ClassJoinCode jc = joinRepo.findByCodeAndActiveTrueAndExpiresAtAfter(cleanCode, now).orElse(null);
    if (jc == null) {
      model.addAttribute("error", "Join code is invalid, closed, or expired.");
      return "student/join";
    }

    UserAccount student = userRepo.findByUsername(auth.getName()).orElseThrow();

    // Try to sync from student_registry if possible
    registrySync.syncAndClaimIfPresent(student);

    // ✅ Effective values come from join code first, then fall back to student profile
    Cohort effCohort = (jc.getCohort() != null) ? jc.getCohort() : student.getCohort();
    Integer effGroup = (jc.getGroupNo() != null) ? jc.getGroupNo() : student.getGroupNo();
    ShiftTime effShift = (jc.getShiftTime() != null) ? jc.getShiftTime() : student.getShiftTime();

    if (effCohort == null || effGroup == null || effShift == null) {
      model.addAttribute("error", "Cannot determine Cohort/Group/Shift for this join. Please contact admin.");
      return "student/join";
    }

    // Only enforce mismatch if student profile actually has values (don’t block new/un-synced students)
    boolean cohortOk = (jc.getCohort() == null) || (student.getCohort() == null)
        || Objects.equals(student.getCohort().getId(), jc.getCohort().getId());
    boolean groupOk = (jc.getGroupNo() == null) || (student.getGroupNo() == null)
        || Objects.equals(student.getGroupNo(), jc.getGroupNo());
    boolean shiftOk = (jc.getShiftTime() == null) || (student.getShiftTime() == null)
        || student.getShiftTime() == jc.getShiftTime();

    if (!cohortOk || !groupOk || !shiftOk) {
      model.addAttribute("error", "This join code is not for your Cohort/Group/Shift.");
      return "student/join";
    }

    List<TeachingSchedule> schedules = teachingRepo.findForStudent(
        jc.getSemester().getId(),
        effCohort.getId(),
        effGroup,
        effShift
    );

    if (jc.getScheduleId() != null) {
      schedules = schedules.stream()
          .filter(t -> Objects.equals(t.getId(), jc.getScheduleId()))
          .toList();
    }

    if (schedules.isEmpty()) {
      model.addAttribute("error", "No teaching schedule found for this join code.");
      return "student/join";
    }

    model.addAttribute("joinCode", jc);
    model.addAttribute("schedules", schedules);
    return "student/join";
  }

  @PostMapping("/{code}")
  @Transactional
  public String doJoin(@PathVariable String code, Authentication auth) {
    String cleanCode = normalizeCode(code);
    var now = LocalDateTime.now(ZONE);

    if (auth == null) return "redirect:/login";

    ClassJoinCode jc = joinRepo.findByCodeAndActiveTrueAndExpiresAtAfter(cleanCode, now).orElse(null);
    if (jc == null) return "redirect:/student?msg=join_code_invalid";

    UserAccount student = userRepo.findByUsername(auth.getName()).orElseThrow();

    // Try to sync from registry if possible
    registrySync.syncAndClaimIfPresent(student);

    // ✅ Effective values come from join code first
    Cohort effCohort = (jc.getCohort() != null) ? jc.getCohort() : student.getCohort();
    Integer effGroup = (jc.getGroupNo() != null) ? jc.getGroupNo() : student.getGroupNo();
    ShiftTime effShift = (jc.getShiftTime() != null) ? jc.getShiftTime() : student.getShiftTime();

    if (effCohort == null || effGroup == null || effShift == null) {
      return "redirect:/student?msg=profile_incomplete";
    }

    // Optional: fill missing student fields from join code so future pages work
    boolean changed = false;
    if (student.getCohort() == null && jc.getCohort() != null) { student.setCohort(jc.getCohort()); changed = true; }
    if (student.getGroupNo() == null && jc.getGroupNo() != null) { student.setGroupNo(jc.getGroupNo()); changed = true; }
    if (student.getShiftTime() == null && jc.getShiftTime() != null) { student.setShiftTime(jc.getShiftTime()); changed = true; }
    if (changed) userRepo.save(student);

    List<TeachingSchedule> schedules = teachingRepo.findForStudent(
        jc.getSemester().getId(),
        effCohort.getId(),
        effGroup,
        effShift
    );

    if (jc.getScheduleId() != null) {
      schedules = schedules.stream()
          .filter(t -> Objects.equals(t.getId(), jc.getScheduleId()))
          .toList();
      if (schedules.isEmpty()) return "redirect:/student?msg=no_schedule";
    }

    int cohortNo = effCohort.getCohortNo();
    String sectionName = "C" + cohortNo + "-G" + effGroup + "-" + effShift.name();

    for (TeachingSchedule ts : schedules) {
      ClassSection sec = sectionRepo
          .findFirstBySemester_IdAndCourse_IdAndTeacher_IdAndShiftTimeAndBuildingAndRoomAndSectionName(
              ts.getSemester().getId(),
              ts.getCourse().getId(),
              ts.getTeacher().getId(),
              ts.getShiftTime(),
              ts.getBuilding(),
              ts.getRoom(),
              sectionName
          )
          .orElseGet(() -> {
            ClassSection s = new ClassSection();
            s.setSemester(ts.getSemester());
            s.setCourse(ts.getCourse());
            s.setTeacher(ts.getTeacher());
            s.setShiftTime(ts.getShiftTime());
            s.setBuilding(ts.getBuilding());
            s.setRoom(ts.getRoom());
            s.setSectionName(sectionName);

            // ✅ you have cohort field in ClassSection, but you never set it today
            // (ClassSection has cohort at :contentReference[oaicite:2]{index=2})
            s.setCohort(effCohort);

            return sectionRepo.save(s);
          });

      if (!enrollRepo.existsByStudent_IdAndSection_Id(student.getId(), sec.getId())) {
        Enrollment e = new Enrollment();
        e.setStudent(student);
        e.setSection(sec);
        enrollRepo.save(e);
      }
    }

    return "redirect:/student?msg=joined";
  }

  private static String normalizeCode(String code) {
    if (code == null) return "";
    return code.trim().replaceAll("\\s+", "").toUpperCase();
  }
}
