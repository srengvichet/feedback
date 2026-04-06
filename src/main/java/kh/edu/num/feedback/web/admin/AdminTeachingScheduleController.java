package kh.edu.num.feedback.web.admin;

import kh.edu.num.feedback.domain.entity.*;
import kh.edu.num.feedback.domain.repo.*;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/teaching")
public class AdminTeachingScheduleController {

  private final TeachingScheduleRepository teachingRepo;
  private final SemesterRepository semesterRepo;
  private final CourseRepository courseRepo;
  private final UserAccountRepository userRepo;
  private final CohortRepository cohortRepo;
  private final ClassSectionRepository sectionRepo;
  private final CohortGroupRepository cohortGroupRepo;

  public AdminTeachingScheduleController(
      TeachingScheduleRepository teachingRepo,
      SemesterRepository semesterRepo,
      CourseRepository courseRepo,
      UserAccountRepository userRepo,
      CohortRepository cohortRepo,
      ClassSectionRepository sectionRepo,
      CohortGroupRepository cohortGroupRepo) {

    this.teachingRepo = teachingRepo;
    this.semesterRepo = semesterRepo;
    this.courseRepo = courseRepo;
    this.userRepo = userRepo;
    this.cohortRepo = cohortRepo;
    this.sectionRepo = sectionRepo;
    this.cohortGroupRepo = cohortGroupRepo;
  }

  // =====================================================
  // LIST PAGE
  // =====================================================
  @GetMapping
  public String page(Model model,
      @RequestParam(required = false) Long semesterId,
      @RequestParam(required = false) Long cohortId,
      @RequestParam(required = false) Integer groupNo,
      @RequestParam(required = false) ShiftTime shiftTime,
      @RequestParam(required = false) Weekday weekday,
      @RequestParam(required = false) String faculty,
      @RequestParam(required = false) String msg,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {

    // Prefer fetch-join version if you add it, otherwise keep findAll()
    List<TeachingSchedule> teachings;
    try {
      teachings = teachingRepo.findAllWithSection();
    } catch (Exception ex) {
      teachings = teachingRepo.findAll();
    }

    teachings = teachings.stream()
        .filter(t -> {
          ClassSection cs = t.getClassSection();
          if (semesterId != null) {
            Long sid = (cs != null && cs.getSemester() != null) ? cs.getSemester().getId()
                : (t.getSemester() != null ? t.getSemester().getId() : null);
            if (!semesterId.equals(sid))
              return false;
          }
          if (cohortId != null) {
            Long cid = (cs != null && cs.getCohort() != null) ? cs.getCohort().getId()
                : (t.getCohort() != null ? t.getCohort().getId() : null);
            if (!cohortId.equals(cid))
              return false;
          }
          if (groupNo != null) {
            Integer g = (cs != null) ? cs.getGroupNo() : t.getGroupNo();
            if (!groupNo.equals(g))
              return false;
          }
          if (shiftTime != null) {
            ShiftTime st = (cs != null) ? cs.getShiftTime() : t.getShiftTime();
            if (st != shiftTime)
              return false;
          }
          if (weekday != null) {
            if (t.getWeekday() != weekday)
              return false;
          }
          if (faculty != null && !faculty.isBlank()) {
            Cohort tCohort = (cs != null && cs.getCohort() != null) ? cs.getCohort()
                : t.getCohort();
            String tFac = (tCohort != null && tCohort.getFaculty() != null) ? tCohort.getFaculty() : "";
            if (!faculty.equals(tFac))
              return false;
          }
          return true;
        })
        .collect(Collectors.toList());

    teachings.sort(scheduleSort());

    // ✅ Pagination (keep same controller structure; just slice after filtering)
    int allCount = teachings.size();

    // allow only common sizes (optional safety)
    if (size != 10 && size != 20 && size != 50 && size != 100) size = 20;
    if (page <= 0) page = 1;

    int totalPages = (int) Math.ceil(allCount / (double) size);
    if (totalPages == 0) totalPages = 1;
    if (page > totalPages) page = totalPages;

    int from = (page - 1) * size;
    int to = Math.min(from + size, allCount);

    List<TeachingSchedule> pageTeachings =
        (from >= allCount) ? List.of() : teachings.subList(from, to);

    model.addAttribute("teachings", pageTeachings);
    model.addAttribute("semesters", semesterRepo.findAll());
    List<Course> allCourses = courseRepo.findAllByOrderByStudyYearAscSemesterNoAscCodeAsc();
    model.addAttribute("courses", allCourses);
    List<Map<String, Object>> courseJson = allCourses.stream().map(c -> {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("id", c.getId());
      m.put("code", c.getCode());
      m.put("name", c.getName());
      m.put("year", c.getStudyYear());
      m.put("sem", c.getSemesterNo());
      return m;
    }).collect(Collectors.toList());
    model.addAttribute("courseJson", courseJson);
    model.addAttribute("teachers", userRepo.findByRole(Role.TEACHER));
    model.addAttribute("cohorts", cohortRepo.findAllByOrderByCohortNoAsc());
    model.addAttribute("shifts", ShiftTime.values());
    model.addAttribute("weekdays", Weekday.values());

    model.addAttribute("semesterId", semesterId);
    model.addAttribute("cohortId", cohortId);
    model.addAttribute("groupNo", groupNo);
    model.addAttribute("shiftTime", shiftTime);
    model.addAttribute("weekday", weekday);
    model.addAttribute("faculty", faculty);
    model.addAttribute("faculties", cohortRepo.findAllByOrderByCohortNoAsc().stream()
        .map(Cohort::getFaculty)
        .filter(f -> f != null && !f.isBlank())
        .distinct().sorted().toList());
    model.addAttribute("msg", msg);

    // ✅ NEW pagination attrs for Thymeleaf
    model.addAttribute("page", page);
    model.addAttribute("size", size);
    model.addAttribute("totalPages", totalPages);
    model.addAttribute("totalRows", allCount);
    model.addAttribute("allCount", allCount);

    return "admin/teaching";
  }

  // =====================================================
  // API: Cohorts
  // =====================================================
  @GetMapping("/api/cohorts")
  @ResponseBody
  public List<Map<String, Object>> apiCohorts() {
    return cohortRepo.findAllByOrderByCohortNoAsc().stream().map(c -> {
      Map<String, Object> m = new HashMap<>();
      m.put("id", c.getId());
      m.put("cohortNo", c.getCohortNo());
      m.put("label", c.getLabel());
      m.put("active", c.isActive());
      return m;
    }).collect(Collectors.toList());
  }

  @PostMapping("/api/sections")
  @ResponseBody
  public ResponseEntity<?> apiCreateSection(@RequestParam Long semesterId,
      @RequestParam Long cohortId,
      @RequestParam Integer groupNo,
      @RequestParam ShiftTime shiftTime,
      @RequestParam(required = false) String building,
      @RequestParam(required = false) String room) {

    if (semesterId == null || cohortId == null || groupNo == null || shiftTime == null) {
      return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", "Missing required fields"));
    }

    Semester sem = semesterRepo.findById(semesterId).orElseThrow();
    Cohort co = cohortRepo.findById(cohortId).orElseThrow();

    String b = trimToNull(building);
    String r = trimToNull(room);

    // Prevent duplicates: same Semester + Cohort + Group + Shift + Building + Room
    Optional<ClassSection> exists = sectionRepo
        .findFirstBySemester_IdAndCohort_IdAndGroupNoAndShiftTimeAndBuildingAndRoom(
            sem.getId(), co.getId(), groupNo, shiftTime, b, r);

    if (exists.isPresent()) {
      return ResponseEntity.ok(Map.of("ok", true, "id", exists.get().getId(), "msg", "Already exists"));
    }

    ClassSection cs = new ClassSection();
    cs.setSemester(sem);
    cs.setCohort(co);
    cs.setGroupNo(groupNo);
    cs.setShiftTime(shiftTime);
    cs.setBuilding(b);
    cs.setRoom(r);
    cs.setSectionName("C" + co.getCohortNo() + "-G" + groupNo);

    ClassSection saved = sectionRepo.save(cs);

    return ResponseEntity.ok(Map.of("ok", true, "id", saved.getId()));
  }

  // =====================================================
  // API: Create Cohort (AJAX)
  // =====================================================
  @PostMapping("/api/cohorts")
  @ResponseBody
  public ResponseEntity<?> apiCreateCohort(@RequestParam Integer cohortNo,
      @RequestParam(required = false) String label) {
    if (cohortNo == null || cohortNo <= 0) {
      return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", "Invalid cohortNo"));
    }

    Cohort c = new Cohort();
    c.setCohortNo(cohortNo);
    if (label != null && label.isBlank())
      label = null;
    c.setLabel(label);

    try {
      Cohort saved = cohortRepo.save(c);
      return ResponseEntity
          .ok(Map.of("ok", true, "id", saved.getId(), "cohortNo", saved.getCohortNo(), "label", saved.getLabel()));
    } catch (Exception ex) {
      return ResponseEntity.ok(Map.of("ok", false, "msg", "Duplicate cohort or save error"));
    }
  }

  // =====================================================
  // API: Groups from ClassSection (Semester + Cohort)
  // =====================================================
  @GetMapping("/api/groups")
  @ResponseBody
  public Map<String, Object> apiGroups(@RequestParam(required = false) Long semesterId,
      @RequestParam Long cohortId) {

    List<Integer> groupNos;
    Map<Integer, Map<String, Object>> defaultsByGroup = new HashMap<>();

    if (semesterId != null) {
      // When semester is selected: load from ClassSection (with shift/room defaults)
      List<ClassSection> secs = sectionRepo.findAll().stream()
          .filter(s -> s.getSemester() != null && semesterId.equals(s.getSemester().getId()))
          .filter(s -> s.getCohort() != null && cohortId.equals(s.getCohort().getId()))
          .collect(Collectors.toList());

      groupNos = secs.stream()
          .map(ClassSection::getGroupNo)
          .filter(Objects::nonNull)
          .distinct()
          .sorted()
          .collect(Collectors.toList());

      for (Integer g : groupNos) {
        ClassSection s = secs.stream().filter(x -> g.equals(x.getGroupNo())).findFirst().orElse(null);
        if (s == null) continue;
        Map<String, Object> d = new HashMap<>();
        d.put("shiftTime", s.getShiftTime() != null ? s.getShiftTime().name() : null);
        d.put("building", s.getBuilding());
        d.put("room", s.getRoom());
        defaultsByGroup.put(g, d);
      }
    } else {
      // No semester selected: load all groups from CohortGroup table
      groupNos = cohortGroupRepo.findByCohort_IdOrderByGroupNoAsc(cohortId)
          .stream()
          .map(CohortGroup::getGroupNo)
          .filter(Objects::nonNull)
          .distinct()
          .sorted()
          .collect(Collectors.toList());
    }

    return Map.of("groupNos", groupNos, "defaultsByGroup", defaultsByGroup);
  }

  // =====================================================
  // BULK CREATE (5 subjects) — now binds to ClassSection
  // =====================================================
  @PostMapping("/bulk")
  public String bulkCreate(
      @RequestParam Long semesterId,
      @RequestParam Long cohortId,
      @RequestParam Integer groupNo,
      @RequestParam ShiftTime shiftTime,
      @RequestParam(required = false) String building,
      @RequestParam(required = false) String room,
      @RequestParam List<Weekday> weekdays,
      @RequestParam List<Long> courseIds,
      @RequestParam List<Long> teacherIds) {

    if (weekdays.isEmpty() || weekdays.size() != courseIds.size() || weekdays.size() != teacherIds.size())
      return "redirect:/admin/teaching?msg=invalid";

    if (new HashSet<>(weekdays).size() != weekdays.size())
      return "redirect:/admin/teaching?msg=dupDay";

    Semester semester = semesterRepo.findById(semesterId).orElseThrow();
    Cohort cohort = cohortRepo.findById(cohortId).orElseThrow();

    String b = trimToNull(building);
    String r = trimToNull(room);

    // ✅ Ensure ONE ClassSection for the group
    ClassSection section = ensureClassSection(semester, cohort, groupNo, shiftTime, b, r);

    for (int i = 0; i < weekdays.size(); i++) {
      Course course = courseRepo.findById(courseIds.get(i)).orElseThrow();
      UserAccount teacher = userRepo.findById(teacherIds.get(i)).orElseThrow();

      TeachingSchedule t = new TeachingSchedule();

      // Keep old fields for compatibility (optional)
      t.setSemester(semester);
      t.setCohort(cohort);
      t.setGroupNo(groupNo);
      t.setShiftTime(shiftTime);
      t.setBuilding(b);
      t.setRoom(r);

      // New canonical link
      t.setClassSection(section);

      t.setWeekday(weekdays.get(i));
      t.setSubjectNo(i + 1);
      t.setCourse(course);
      t.setTeacher(teacher);

      teachingRepo.save(t);
    }

    return "redirect:/admin/teaching?msg=bulkCreated";
  }

  // =====================================================
  // Ensure section
  // =====================================================
  private ClassSection ensureClassSection(
      Semester semester,
      Cohort cohort,
      Integer groupNo,
      ShiftTime shiftTime,
      String building,
      String room) {

    String b = trimToNull(building);
    String r = trimToNull(room);

    return sectionRepo
        .findFirstBySemester_IdAndCohort_IdAndGroupNoAndShiftTimeAndBuildingAndRoom(
            semester.getId(), cohort.getId(), groupNo, shiftTime, b, r)
        .orElseGet(() -> {
          ClassSection cs = new ClassSection();
          cs.setSemester(semester);
          cs.setCohort(cohort);
          cs.setGroupNo(groupNo);
          cs.setShiftTime(shiftTime);
          cs.setBuilding(b);
          cs.setRoom(r);
          cs.setSectionName("C" + cohort.getCohortNo() + "-G" + groupNo);
          return sectionRepo.save(cs);
        });
  }

  private static String trimToNull(String s) {
    if (s == null)
      return null;
    String t = s.trim();
    return t.isBlank() ? null : t;
  }

  private static Comparator<TeachingSchedule> scheduleSort() {
    return Comparator
        .comparing((TeachingSchedule t) -> {
          ClassSection cs = t.getClassSection();
          return (cs != null && cs.getSemester() != null) ? cs.getSemester().getName()
              : (t.getSemester() != null ? t.getSemester().getName() : "");
        })
        .thenComparing(t -> {
          ClassSection cs = t.getClassSection();
          Integer cno = (cs != null && cs.getCohort() != null) ? cs.getCohort().getCohortNo()
              : (t.getCohort() != null ? t.getCohort().getCohortNo() : null);
          return cno != null ? cno : 999999;
        })
        .thenComparing(t -> {
          ClassSection cs = t.getClassSection();
          Integer g = (cs != null) ? cs.getGroupNo() : t.getGroupNo();
          return g != null ? g : 999999;
        })
        .thenComparing(t -> {
          ClassSection cs = t.getClassSection();
          ShiftTime st = (cs != null) ? cs.getShiftTime() : t.getShiftTime();
          return st != null ? st.name() : "";
        })
        .thenComparing(t -> t.getSubjectNo() != null ? t.getSubjectNo() : 999)
        .thenComparing(t -> t.getWeekday() != null ? t.getWeekday().name() : "");
  }
}







// package kh.edu.num.feedback.web.admin;

// import kh.edu.num.feedback.domain.entity.*;
// import kh.edu.num.feedback.domain.repo.*;
// import org.springframework.http.ResponseEntity;
// import org.springframework.stereotype.Controller;
// import org.springframework.ui.Model;
// import org.springframework.web.bind.annotation.*;

// import java.util.*;
// import java.util.stream.Collectors;

// @Controller
// @RequestMapping("/admin/teaching")
// public class AdminTeachingScheduleController {

//   private final TeachingScheduleRepository teachingRepo;
//   private final SemesterRepository semesterRepo;
//   private final CourseRepository courseRepo;
//   private final UserAccountRepository userRepo;
//   private final CohortRepository cohortRepo;
//   private final ClassSectionRepository sectionRepo;

//   public AdminTeachingScheduleController(
//       TeachingScheduleRepository teachingRepo,
//       SemesterRepository semesterRepo,
//       CourseRepository courseRepo,
//       UserAccountRepository userRepo,
//       CohortRepository cohortRepo,
//       ClassSectionRepository sectionRepo) {

//     this.teachingRepo = teachingRepo;
//     this.semesterRepo = semesterRepo;
//     this.courseRepo = courseRepo;
//     this.userRepo = userRepo;
//     this.cohortRepo = cohortRepo;
//     this.sectionRepo = sectionRepo;
//   }

//   // =====================================================
//   // LIST PAGE
//   // =====================================================
//   @GetMapping
//   public String page(Model model,
//       @RequestParam(required = false) Long semesterId,
//       @RequestParam(required = false) Long cohortId,
//       @RequestParam(required = false) Integer groupNo,
//       @RequestParam(required = false) ShiftTime shiftTime,
//       @RequestParam(required = false) Weekday weekday,
//       @RequestParam(required = false) String msg,
//       @RequestParam(defaultValue = "1") int page,
//       @RequestParam(defaultValue = "20") int size) {

//     // Prefer fetch-join version if you add it, otherwise keep findAll()
//     List<TeachingSchedule> teachings;
//     try {
//       teachings = teachingRepo.findAllWithSection();
//     } catch (Exception ex) {
//       teachings = teachingRepo.findAll();
//     }

//     // ✅ Filter (same logic)
//     teachings = teachings.stream()
//         .filter(t -> {
//           ClassSection cs = t.getClassSection();
//           if (semesterId != null) {
//             Long sid = (cs != null && cs.getSemester() != null) ? cs.getSemester().getId()
//                 : (t.getSemester() != null ? t.getSemester().getId() : null);
//             if (!semesterId.equals(sid))
//               return false;
//           }
//           if (cohortId != null) {
//             Long cid = (cs != null && cs.getCohort() != null) ? cs.getCohort().getId()
//                 : (t.getCohort() != null ? t.getCohort().getId() : null);
//             if (!cohortId.equals(cid))
//               return false;
//           }
//           if (groupNo != null) {
//             Integer g = (cs != null) ? cs.getGroupNo() : t.getGroupNo();
//             if (!groupNo.equals(g))
//               return false;
//           }
//           if (shiftTime != null) {
//             ShiftTime st = (cs != null) ? cs.getShiftTime() : t.getShiftTime();
//             if (st != shiftTime)
//               return false;
//           }
//           if (weekday != null) {
//             if (t.getWeekday() != weekday)
//               return false;
//           }
//           return true;
//         })
//         .collect(Collectors.toList());

//     teachings.sort(scheduleSort());

//     // ✅ NEW: totals + paging slice
//     int allCount = teachings.size();
//     if (size <= 0)
//       size = 20;
//     if (page <= 0)
//       page = 1;

//     int totalPages = (int) Math.ceil(allCount / (double) size);
//     if (totalPages == 0)
//       totalPages = 1;
//     if (page > totalPages)
//       page = totalPages;

//     int from = (page - 1) * size;
//     int to = Math.min(from + size, allCount);

//     List<TeachingSchedule> pageTeachings = (from >= allCount) ? List.of() : teachings.subList(from, to);

//     model.addAttribute("teachings", pageTeachings);
//     model.addAttribute("semesters", semesterRepo.findAll());
//     model.addAttribute("courses", courseRepo.findAll());
//     model.addAttribute("teachers", userRepo.findByRole(Role.TEACHER));
//     model.addAttribute("cohorts", cohortRepo.findAllByOrderByCohortNoAsc());
//     model.addAttribute("shifts", ShiftTime.values());
//     model.addAttribute("weekdays", Weekday.values());

//     model.addAttribute("semesterId", semesterId);
//     model.addAttribute("cohortId", cohortId);
//     model.addAttribute("groupNo", groupNo);
//     model.addAttribute("shiftTime", shiftTime);
//     model.addAttribute("weekday", weekday);
//     model.addAttribute("msg", msg);
//     // ✅ NEW pagination attrs
//     model.addAttribute("page", page);
//     model.addAttribute("size", size);
//     model.addAttribute("totalPages", totalPages);
//     model.addAttribute("totalRows", allCount);
//     model.addAttribute("allCount", allCount);
//     return "admin/teaching";
//   }

//   // =====================================================
//   // API: Cohorts
//   // =====================================================
//   @GetMapping("/api/cohorts")
//   @ResponseBody
//   public List<Map<String, Object>> apiCohorts() {
//     return cohortRepo.findAllByOrderByCohortNoAsc().stream().map(c -> {
//       Map<String, Object> m = new HashMap<>();
//       m.put("id", c.getId());
//       m.put("cohortNo", c.getCohortNo());
//       m.put("label", c.getLabel());
//       m.put("active", c.isActive());
//       return m;
//     }).collect(Collectors.toList());
//   }

//   @PostMapping("/api/sections")
//   @ResponseBody
//   public ResponseEntity<?> apiCreateSection(@RequestParam Long semesterId,
//       @RequestParam Long cohortId,
//       @RequestParam Integer groupNo,
//       @RequestParam ShiftTime shiftTime,
//       @RequestParam(required = false) String building,
//       @RequestParam(required = false) String room) {

//     if (semesterId == null || cohortId == null || groupNo == null || shiftTime == null) {
//       return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", "Missing required fields"));
//     }

//     Semester sem = semesterRepo.findById(semesterId).orElseThrow();
//     Cohort co = cohortRepo.findById(cohortId).orElseThrow();

//     String b = trimToNull(building);
//     String r = trimToNull(room);

//     // Prevent duplicates: same Semester + Cohort + Group + Shift + Building + Room
//     Optional<ClassSection> exists = sectionRepo
//         .findFirstBySemester_IdAndCohort_IdAndGroupNoAndShiftTimeAndBuildingAndRoom(
//             sem.getId(), co.getId(), groupNo, shiftTime, b, r);

//     if (exists.isPresent()) {
//       return ResponseEntity.ok(Map.of("ok", true, "id", exists.get().getId(), "msg", "Already exists"));
//     }

//     ClassSection cs = new ClassSection();
//     cs.setSemester(sem);
//     cs.setCohort(co);
//     cs.setGroupNo(groupNo);
//     cs.setShiftTime(shiftTime);
//     cs.setBuilding(b);
//     cs.setRoom(r);
//     cs.setSectionName("C" + co.getCohortNo() + "-G" + groupNo);

//     ClassSection saved = sectionRepo.save(cs);

//     return ResponseEntity.ok(Map.of("ok", true, "id", saved.getId()));
//   }

//   // =====================================================
//   // API: Create Cohort (AJAX)
//   // =====================================================
//   @PostMapping("/api/cohorts")
//   @ResponseBody
//   public ResponseEntity<?> apiCreateCohort(@RequestParam Integer cohortNo,
//       @RequestParam(required = false) String label) {
//     if (cohortNo == null || cohortNo <= 0) {
//       return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", "Invalid cohortNo"));
//     }

//     Cohort c = new Cohort();
//     c.setCohortNo(cohortNo);
//     if (label != null && label.isBlank())
//       label = null;
//     c.setLabel(label);

//     try {
//       Cohort saved = cohortRepo.save(c);
//       return ResponseEntity
//           .ok(Map.of("ok", true, "id", saved.getId(), "cohortNo", saved.getCohortNo(), "label", saved.getLabel()));
//     } catch (Exception ex) {
//       return ResponseEntity.ok(Map.of("ok", false, "msg", "Duplicate cohort or save error"));
//     }
//   }

//   // =====================================================
//   // API: Groups from ClassSection (Semester + Cohort)
//   // =====================================================
//   @GetMapping("/api/groups")
//   @ResponseBody
//   public Map<String, Object> apiGroups(@RequestParam Long semesterId,
//       @RequestParam Long cohortId) {

//     List<ClassSection> secs = sectionRepo.findAll().stream()
//         .filter(s -> s.getSemester() != null && semesterId.equals(s.getSemester().getId()))
//         .filter(s -> s.getCohort() != null && cohortId.equals(s.getCohort().getId()))
//         .collect(Collectors.toList());

//     List<Integer> groupNos = secs.stream()
//         .map(ClassSection::getGroupNo)
//         .filter(Objects::nonNull)
//         .distinct()
//         .sorted()
//         .collect(Collectors.toList());

//     Map<Integer, Map<String, Object>> defaultsByGroup = new HashMap<>();
//     for (Integer g : groupNos) {
//       ClassSection s = secs.stream().filter(x -> g.equals(x.getGroupNo())).findFirst().orElse(null);
//       if (s == null)
//         continue;

//       Map<String, Object> d = new HashMap<>();
//       d.put("shiftTime", s.getShiftTime() != null ? s.getShiftTime().name() : null);
//       d.put("building", s.getBuilding());
//       d.put("room", s.getRoom());
//       defaultsByGroup.put(g, d);
//     }

//     return Map.of("groupNos", groupNos, "defaultsByGroup", defaultsByGroup);
//   }

//   // =====================================================
//   // BULK CREATE (5 subjects) — now binds to ClassSection
//   // =====================================================
//   @PostMapping("/bulk")
//   public String bulkCreate(
//       @RequestParam Long semesterId,
//       @RequestParam Long cohortId,
//       @RequestParam Integer groupNo,
//       @RequestParam ShiftTime shiftTime,
//       @RequestParam(required = false) String building,
//       @RequestParam(required = false) String room,
//       @RequestParam List<Weekday> weekdays,
//       @RequestParam List<Long> courseIds,
//       @RequestParam List<Long> teacherIds) {

//     if (weekdays.size() != 5 || courseIds.size() != 5 || teacherIds.size() != 5)
//       return "redirect:/admin/teaching?msg=invalid";

//     if (new HashSet<>(weekdays).size() != weekdays.size())
//       return "redirect:/admin/teaching?msg=dupDay";

//     Semester semester = semesterRepo.findById(semesterId).orElseThrow();
//     Cohort cohort = cohortRepo.findById(cohortId).orElseThrow();

//     String b = trimToNull(building);
//     String r = trimToNull(room);

//     // ✅ Ensure ONE ClassSection for the group
//     ClassSection section = ensureClassSection(semester, cohort, groupNo, shiftTime, b, r);

//     for (int i = 0; i < 5; i++) {
//       Course course = courseRepo.findById(courseIds.get(i)).orElseThrow();
//       UserAccount teacher = userRepo.findById(teacherIds.get(i)).orElseThrow();

//       TeachingSchedule t = new TeachingSchedule();

//       // Keep old fields for compatibility (optional)
//       t.setSemester(semester);
//       t.setCohort(cohort);
//       t.setGroupNo(groupNo);
//       t.setShiftTime(shiftTime);
//       t.setBuilding(b);
//       t.setRoom(r);

//       // New canonical link
//       t.setClassSection(section);

//       t.setWeekday(weekdays.get(i));
//       t.setSubjectNo(i + 1);
//       t.setCourse(course);
//       t.setTeacher(teacher);

//       teachingRepo.save(t);
//     }

//     return "redirect:/admin/teaching?msg=bulkCreated";
//   }

//   // =====================================================
//   // Ensure section
//   // =====================================================
//   private ClassSection ensureClassSection(
//       Semester semester,
//       Cohort cohort,
//       Integer groupNo,
//       ShiftTime shiftTime,
//       String building,
//       String room) {

//     String b = trimToNull(building);
//     String r = trimToNull(room);

//     return sectionRepo
//         .findFirstBySemester_IdAndCohort_IdAndGroupNoAndShiftTimeAndBuildingAndRoom(
//             semester.getId(), cohort.getId(), groupNo, shiftTime, b, r)
//         .orElseGet(() -> {
//           ClassSection cs = new ClassSection();
//           cs.setSemester(semester);
//           cs.setCohort(cohort);
//           cs.setGroupNo(groupNo);
//           cs.setShiftTime(shiftTime);
//           cs.setBuilding(b);
//           cs.setRoom(r);
//           cs.setSectionName("C" + cohort.getCohortNo() + "-G" + groupNo);
//           return sectionRepo.save(cs);
//         });
//   }

//   private static String trimToNull(String s) {
//     if (s == null)
//       return null;
//     String t = s.trim();
//     return t.isBlank() ? null : t;
//   }

//   private static Comparator<TeachingSchedule> scheduleSort() {
//     return Comparator
//         .comparing((TeachingSchedule t) -> {
//           ClassSection cs = t.getClassSection();
//           return (cs != null && cs.getSemester() != null) ? cs.getSemester().getName()
//               : (t.getSemester() != null ? t.getSemester().getName() : "");
//         })
//         .thenComparing(t -> {
//           ClassSection cs = t.getClassSection();
//           Integer cno = (cs != null && cs.getCohort() != null) ? cs.getCohort().getCohortNo()
//               : (t.getCohort() != null ? t.getCohort().getCohortNo() : null);
//           return cno != null ? cno : 999999;
//         })
//         .thenComparing(t -> {
//           ClassSection cs = t.getClassSection();
//           Integer g = (cs != null) ? cs.getGroupNo() : t.getGroupNo();
//           return g != null ? g : 999999;
//         })
//         .thenComparing(t -> {
//           ClassSection cs = t.getClassSection();
//           ShiftTime st = (cs != null) ? cs.getShiftTime() : t.getShiftTime();
//           return st != null ? st.name() : "";
//         })
//         .thenComparing(t -> t.getSubjectNo() != null ? t.getSubjectNo() : 999)
//         .thenComparing(t -> t.getWeekday() != null ? t.getWeekday().name() : "");
//   }
// }

// // package kh.edu.num.feedback.web.admin;

// // import kh.edu.num.feedback.domain.entity.*;
// // import kh.edu.num.feedback.domain.repo.*;
// // import org.springframework.stereotype.Controller;
// // import org.springframework.ui.Model;
// // import org.springframework.web.bind.annotation.*;

// // import java.util.Comparator;
// // import java.util.HashSet;
// // import java.util.List;
// // import java.util.stream.Collectors;

// // @Controller
// // @RequestMapping("/admin/teaching")
// // public class AdminTeachingScheduleController {

// // private final TeachingScheduleRepository teachingRepo;
// // private final SemesterRepository semesterRepo;
// // private final CourseRepository courseRepo;
// // private final UserAccountRepository userRepo;
// // private final CohortRepository cohortRepo;

// // // ✅ NEW: create sections for bulk enroll
// // private final ClassSectionRepository sectionRepo;

// // public AdminTeachingScheduleController(TeachingScheduleRepository
// // teachingRepo,
// // SemesterRepository semesterRepo,
// // CourseRepository courseRepo,
// // UserAccountRepository userRepo,
// // CohortRepository cohortRepo,
// // ClassSectionRepository sectionRepo) {
// // this.teachingRepo = teachingRepo;
// // this.semesterRepo = semesterRepo;
// // this.courseRepo = courseRepo;
// // this.userRepo = userRepo;
// // this.cohortRepo = cohortRepo;
// // this.sectionRepo = sectionRepo;
// // }

// // // -------------------------
// // // LIST + FILTERS
// // // -------------------------
// // @GetMapping
// // public String page(Model model,
// // @RequestParam(required = false) Long semesterId,
// // @RequestParam(required = false) Long cohortId,
// // @RequestParam(required = false) Integer groupNo,
// // @RequestParam(required = false) ShiftTime shiftTime,
// // @RequestParam(required = false) Weekday weekday,
// // @RequestParam(required = false) String msg) {

// // List<TeachingSchedule> teachings = teachingRepo.findAll();

// // if (semesterId != null) {
// // teachings = teachings.stream()
// // .filter(t -> t.getSemester() != null &&
// // semesterId.equals(t.getSemester().getId()))
// // .collect(Collectors.toList());
// // }

// // if (cohortId != null) {
// // teachings = teachings.stream()
// // .filter(t -> t.getCohort() != null && cohortId.equals(t.getCohort().getId()))
// // .collect(Collectors.toList());
// // }

// // if (groupNo != null) {
// // teachings = teachings.stream()
// // .filter(t -> groupNo.equals(t.getGroupNo()))
// // .collect(Collectors.toList());
// // }

// // if (shiftTime != null) {
// // teachings = teachings.stream()
// // .filter(t -> t.getShiftTime() == shiftTime)
// // .collect(Collectors.toList());
// // }

// // if (weekday != null) {
// // teachings = teachings.stream()
// // .filter(t -> t.getWeekday() == weekday)
// // .collect(Collectors.toList());
// // }

// // teachings.sort(scheduleSort());

// // model.addAttribute("teachings", teachings);
// // model.addAttribute("semesters", semesterRepo.findAll());
// // model.addAttribute("courses", courseRepo.findAll());
// // model.addAttribute("teachers", userRepo.findByRole(Role.TEACHER));
// // model.addAttribute("cohorts", cohortRepo.findAllByOrderByCohortNoAsc());
// // model.addAttribute("shifts", ShiftTime.values());
// // model.addAttribute("weekdays", Weekday.values());

// // model.addAttribute("semesterId", semesterId);
// // model.addAttribute("cohortId", cohortId);
// // model.addAttribute("groupNo", groupNo);
// // model.addAttribute("shiftTime", shiftTime);
// // model.addAttribute("weekday", weekday);

// // model.addAttribute("msg", msg);
// // return "admin/teaching";
// // }

// // @PostMapping("/sync-sections")
// // public String syncSections() {

// // List<TeachingSchedule> all = teachingRepo.findAll();

// // int created = 0;

// // for (TeachingSchedule t : all) {

// // if (t.getSemester() == null ||
// // t.getCourse() == null ||
// // t.getTeacher() == null ||
// // t.getShiftTime() == null) {
// // continue;
// // }

// // String sectionName = buildSectionName(t.getCohort(), t.getGroupNo());
// // String b = trimToNull(t.getBuilding());
// // String r = trimToNull(t.getRoom());

// // var existing = sectionRepo
// // .findFirstBySemester_IdAndCourse_IdAndTeacher_IdAndShiftTimeAndBuildingAndRoomAndSectionName(
// // t.getSemester().getId(),
// // t.getCourse().getId(),
// // t.getTeacher().getId(),
// // t.getShiftTime(),
// // b,
// // r,
// // sectionName
// // );

// // if (existing.isPresent()) continue;

// // ClassSection cs = new ClassSection();
// // cs.setSemester(t.getSemester());
// // cs.setCourse(t.getCourse());
// // cs.setTeacher(t.getTeacher());
// // cs.setShiftTime(t.getShiftTime());
// // cs.setBuilding(b);
// // cs.setRoom(r);
// // cs.setSectionName(sectionName);
// // cs.setCohort(t.getCohort());

// // sectionRepo.save(cs);
// // created++;
// // }

// // return "redirect:/admin/teaching?msg=sync_created_" + created;
// // }

// // // -------------------------
// // // BULK CREATE (5 rows)
// // // -------------------------
// // @PostMapping("/bulk")
// // public String bulkCreate(@RequestParam Long semesterId,
// // @RequestParam(required = false) Long cohortId,
// // @RequestParam(required = false) Integer groupNo,
// // @RequestParam ShiftTime shiftTime,
// // @RequestParam(required = false) String building,
// // @RequestParam(required = false) String room,
// // @RequestParam List<Weekday> weekdays,
// // @RequestParam List<Long> courseIds,
// // @RequestParam List<Long> teacherIds) {

// // if (weekdays.size() != 5 || courseIds.size() != 5 || teacherIds.size() != 5)
// // {
// // return "redirect:/admin/teaching?msg=invalid";
// // }

// // // prevent duplicate weekday in one bulk submit
// // if (new HashSet<>(weekdays).size() != weekdays.size()) {
// // return "redirect:/admin/teaching?msg=dupDay";
// // }

// // Semester semester = semesterRepo.findById(semesterId).orElseThrow();
// // Cohort cohort = (cohortId == null) ? null :
// // cohortRepo.findById(cohortId).orElseThrow();

// // String b = trimToNull(building);
// // String r = trimToNull(room);

// // // ✅ derive a consistent sectionName for class_sections
// // String sectionName = buildSectionName(cohort, groupNo);

// // for (int i = 0; i < 5; i++) {
// // Course course = courseRepo.findById(courseIds.get(i)).orElseThrow();
// // UserAccount teacher = userRepo.findById(teacherIds.get(i)).orElseThrow();

// // TeachingSchedule t = new TeachingSchedule();
// // t.setSemester(semester);
// // t.setCohort(cohort);
// // t.setGroupNo(groupNo);

// // t.setShiftTime(shiftTime);
// // t.setWeekday(weekdays.get(i));

// // t.setBuilding(b);
// // t.setRoom(r);

// // t.setSubjectNo(i + 1); // 1..5
// // t.setCourse(course);
// // t.setTeacher(teacher);

// // teachingRepo.save(t);

// // // ✅ NEW: ensure corresponding ClassSection exists (for Bulk Enroll)
// // ensureClassSection(semester, course, teacher, shiftTime, b, r, sectionName,
// // cohort);
// // }

// // return "redirect:/admin/teaching?msg=bulkCreated";
// // }

// // // -------------------------
// // // EDIT (GET)
// // // -------------------------
// // @GetMapping("/{id}/edit")
// // public String editForm(@PathVariable Long id, Model model) {
// // TeachingSchedule t = teachingRepo.findById(id).orElseThrow();

// // model.addAttribute("t", t);
// // model.addAttribute("semesters", semesterRepo.findAll());
// // model.addAttribute("courses", courseRepo.findAll());
// // model.addAttribute("teachers", userRepo.findByRole(Role.TEACHER));
// // model.addAttribute("cohorts", cohortRepo.findAllByOrderByCohortNoAsc());
// // model.addAttribute("shifts", ShiftTime.values());
// // model.addAttribute("weekdays", Weekday.values());

// // return "admin/teaching_edit";
// // }

// // // EDIT (POST)
// // @PostMapping("/{id}/edit")
// // public String editSave(@PathVariable Long id,
// // @RequestParam Long semesterId,
// // @RequestParam Long courseId,
// // @RequestParam Long teacherId,
// // @RequestParam ShiftTime shiftTime,
// // @RequestParam Weekday weekday,
// // @RequestParam(required = false) Long cohortId,
// // @RequestParam(required = false) Integer groupNo,
// // @RequestParam(required = false) String building,
// // @RequestParam(required = false) String room,
// // @RequestParam(required = false) Integer subjectNo) {

// // TeachingSchedule t = teachingRepo.findById(id).orElseThrow();

// // Semester semester = semesterRepo.findById(semesterId).orElseThrow();
// // Course course = courseRepo.findById(courseId).orElseThrow();
// // UserAccount teacher = userRepo.findById(teacherId).orElseThrow();

// // t.setSemester(semester);
// // t.setCourse(course);
// // t.setTeacher(teacher);

// // t.setShiftTime(shiftTime);
// // t.setWeekday(weekday);

// // Cohort cohort = (cohortId == null) ? null :
// // cohortRepo.findById(cohortId).orElseThrow();
// // t.setCohort(cohort);
// // t.setGroupNo(groupNo);

// // String b = trimToNull(building);
// // String r = trimToNull(room);

// // t.setBuilding(b);
// // t.setRoom(r);
// // t.setSubjectNo(subjectNo);

// // teachingRepo.save(t);

// // // ✅ NEW: ensure section exists after edit too
// // String sectionName = buildSectionName(cohort, groupNo);
// // ensureClassSection(semester, course, teacher, shiftTime, b, r, sectionName,
// // cohort);

// // return "redirect:/admin/teaching?msg=updated";
// // }

// // // -------------------------
// // // DELETE single row
// // // -------------------------
// // @PostMapping("/{id}/delete")
// // public String deleteOne(@PathVariable Long id) {
// // teachingRepo.deleteById(id);
// // return "redirect:/admin/teaching?msg=deleted";
// // }

// // // -------------------------
// // // NEW: Find-or-create ClassSection for Enrollment
// // // -------------------------
// // private void ensureClassSection(Semester semester,
// // Course course,
// // UserAccount teacher,
// // ShiftTime shiftTime,
// // String building,
// // String room,
// // String sectionName,
// // Cohort cohort) {

// // // Normalize nulls to avoid duplicates: null vs ""
// // String b = trimToNull(building);
// // String r = trimToNull(room);
// // String sname = trimToNull(sectionName);

// // var existing = sectionRepo
// // .findFirstBySemester_IdAndCourse_IdAndTeacher_IdAndShiftTimeAndBuildingAndRoomAndSectionName(
// // semester.getId(),
// // course.getId(),
// // teacher.getId(),
// // shiftTime,
// // b,
// // r,
// // sname);

// // if (existing.isPresent())
// // return;

// // ClassSection cs = new ClassSection();
// // cs.setSemester(semester);
// // cs.setCourse(course);
// // cs.setTeacher(teacher);
// // cs.setShiftTime(shiftTime);
// // cs.setBuilding(b);
// // cs.setRoom(r);
// // cs.setSectionName(sname);
// // cs.setCohort(cohort);

// // sectionRepo.save(cs);
// // }

// // // Build sectionName like: C13-G1
// // private static String buildSectionName(Cohort cohort, Integer groupNo) {
// // Integer cno = (cohort != null) ? cohort.getCohortNo() : null;
// // if (cno == null && groupNo == null)
// // return "ALL";
// // if (cno != null && groupNo == null)
// // return "C" + cno;
// // if (cno == null)
// // return "G" + groupNo;
// // return "C" + cno + "-G" + groupNo;
// // }

// // // -------------------------
// // // Helpers
// // // -------------------------
// // private static String trimToNull(String s) {
// // if (s == null)
// // return null;
// // String t = s.trim();
// // return t.isBlank() ? null : t;
// // }

// // private static Comparator<TeachingSchedule> scheduleSort() {
// // return Comparator
// // .comparing((TeachingSchedule t) -> t.getSemester() != null ?
// // t.getSemester().getName() : "")
// // .thenComparing(t -> (t.getCohort() != null && t.getCohort().getCohortNo() !=
// // null)
// // ? t.getCohort().getCohortNo()
// // : 999999)
// // .thenComparing(t -> t.getGroupNo() != null ? t.getGroupNo() : 999999)
// // .thenComparing(t -> t.getShiftTime() != null ? t.getShiftTime().name() : "")
// // .thenComparing(t -> t.getBuilding() != null ? t.getBuilding() : "")
// // .thenComparing(t -> t.getRoom() != null ? t.getRoom() : "")
// // .thenComparing(t -> t.getSubjectNo() != null ? t.getSubjectNo() : 999)
// // .thenComparing(t -> t.getWeekday() != null ? t.getWeekday().name() : "");
// // }
// // }