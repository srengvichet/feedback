package kh.edu.num.feedback.web.student;

import kh.edu.num.feedback.domain.entity.*;
import kh.edu.num.feedback.domain.repo.*;
import kh.edu.num.feedback.service.EvaluationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Controller
@RequestMapping("/student")
public class StudentScheduleController {

  private final UserAccountRepository userRepo;
  private final SemesterRepository semesterRepo;
  private final TeachingScheduleRepository teachingRepo;
  private final EvaluationService evaluationService;

  private static final ZoneId ZONE = ZoneId.of("Asia/Phnom_Penh");

  public StudentScheduleController(UserAccountRepository userRepo,
                                  SemesterRepository semesterRepo,
                                  TeachingScheduleRepository teachingRepo,
                                  EvaluationService evaluationService) {
    this.userRepo = userRepo;
    this.semesterRepo = semesterRepo;
    this.teachingRepo = teachingRepo;
    this.evaluationService = evaluationService;
  }

  @GetMapping("/schedule")
  public String schedule(Model model,
                         Principal principal,
                         @RequestParam(name="day", required=false) Weekday day) {

    UserAccount student = userRepo.findByUsername(principal.getName()).orElseThrow();

    LocalDate today = LocalDate.now(ZONE);
    Semester sem = semesterRepo
        .findFirstByStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateDesc(today, today)
        .orElse(null);

    if (sem == null) {
      model.addAttribute("error", "No active semester today. Ask admin to create semester dates.");
      return "student/schedule";
    }

    Long cohortId = (student.getCohort() == null) ? null : student.getCohort().getId();
    Integer groupNo = student.getGroupNo();
    ShiftTime shiftTime = student.getShiftTime();

    if (cohortId == null || groupNo == null || shiftTime == null) {
      model.addAttribute("error",
          "Your profile is missing Cohort/Group/Shift. Ask admin to set these fields.");
      model.addAttribute("semester", sem);
      return "student/schedule";
    }

    Weekday todayWeekday = Weekday.valueOf(today.getDayOfWeek().name());
    Weekday selectedDay = (day != null) ? day : todayWeekday;

    List<TeachingSchedule> schedules =
        teachingRepo.findForStudentToday(sem.getId(), cohortId, groupNo, shiftTime, selectedDay);

    boolean windowOpen = evaluationService.isWindowOpenForSection(
      sem.getId(),
      EvaluationKind.STUDENT_FEEDBACK,
      cohortId,
      groupNo
    );

    model.addAttribute("semester", sem);
    model.addAttribute("student", student);
    model.addAttribute("schedules", schedules);
    model.addAttribute("todayWeekday", todayWeekday);
    model.addAttribute("selectedDay", selectedDay);
    model.addAttribute("weekdays", Weekday.values());
    model.addAttribute("windowOpen", windowOpen);

    return "student/schedule";
  }
}
