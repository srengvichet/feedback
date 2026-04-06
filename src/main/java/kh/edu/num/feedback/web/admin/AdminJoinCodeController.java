package kh.edu.num.feedback.web.admin;

import kh.edu.num.feedback.domain.entity.*;
import kh.edu.num.feedback.domain.repo.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

// add imports
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.client.j2se.MatrixToImageWriter;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

@Controller
@RequestMapping("/admin/join-codes")
public class AdminJoinCodeController {

  private final SemesterRepository semesterRepo;
  private final CohortRepository cohortRepo;
  private final TeachingScheduleRepository teachingRepo;
  private final ClassJoinCodeRepository joinRepo;

  private static final ZoneId ZONE = ZoneId.of("Asia/Phnom_Penh");
  private static final Random RNG = new Random();
  private static final char[] ALPH = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

  public AdminJoinCodeController(SemesterRepository semesterRepo,
      CohortRepository cohortRepo,
      TeachingScheduleRepository teachingRepo,
      ClassJoinCodeRepository joinRepo) {
    this.semesterRepo = semesterRepo;
    this.cohortRepo = cohortRepo;
    this.teachingRepo = teachingRepo;
    this.joinRepo = joinRepo;
  }

  @GetMapping
  public String page(Model model,
      @RequestParam(required = false) Long semesterId,
      @RequestParam(required = false) String msg) {

    // inside page(...)
    String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    model.addAttribute("baseUrl", baseUrl);

    model.addAttribute("semesters", semesterRepo.findAll());
    model.addAttribute("semesterId", semesterId);
    model.addAttribute("msg", msg);

    if (semesterId != null) {
      var joinCodes = joinRepo.findBySemester_IdOrderByIdDesc(semesterId);
      model.addAttribute("joinCodes", joinCodes);

      Map<Long, String> subjectLabelByScheduleId = new HashMap<>();
      var scheduleIds = joinCodes.stream()
          .map(ClassJoinCode::getScheduleId)
          .filter(Objects::nonNull)
          .distinct()
          .toList();

      if (!scheduleIds.isEmpty()) {
        var schedules = teachingRepo.findAllById(scheduleIds);
        for (var t : schedules) {
          String label = "#" + (t.getSubjectNo() != null ? t.getSubjectNo() : "-")
              + " | " + (t.getWeekday() != null ? t.getWeekday().name() : "-")
              + " | " + (t.getCourse() != null ? (t.getCourse().getCode() + " - " + t.getCourse().getName()) : "-")
              + " | " + (t.getTeacher() != null ? t.getTeacher().getUsername() : "-");
          subjectLabelByScheduleId.put(t.getId(), label);
        }
      }
      model.addAttribute("subjectLabelByScheduleId", subjectLabelByScheduleId);

    } else {
      model.addAttribute("joinCodes", List.of());
      model.addAttribute("subjectLabelByScheduleId", Map.of());
    }

    return "admin/join_codes";
  }

  @GetMapping(value = "/{id}/qr", produces = MediaType.IMAGE_PNG_VALUE)
  @ResponseBody
  public ResponseEntity<byte[]> qr(@PathVariable Long id) {
    var jc = joinRepo.findById(id).orElseThrow();

    String joinUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
        .path("/student/join/")
        .path(jc.getCode())
        .toUriString();

    byte[] png = qrPng(joinUrl, 280);

    return ResponseEntity.ok()
        .contentType(MediaType.IMAGE_PNG)
        .cacheControl(CacheControl.noCache())
        .body(png);
  }

  private static byte[] qrPng(String text, int size) {
    try {
      Map<EncodeHintType, Object> hints = Map.of(EncodeHintType.MARGIN, 1);
      BitMatrix matrix = new MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints);
      BufferedImage image = MatrixToImageWriter.toBufferedImage(matrix);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ImageIO.write(image, "PNG", out);
      return out.toByteArray();
    } catch (Exception e) {
      throw new RuntimeException("QR generation failed", e);
    }
  }

  @PostMapping("/generate")
  public String generate(@RequestParam Long semesterId,
      @RequestParam Long cohortId,
      @RequestParam Integer groupNo,
      @RequestParam ShiftTime shiftTime,
      @RequestParam(required = false) Long scheduleId) {

    var sem = semesterRepo.findById(semesterId).orElseThrow();
    var cohort = cohortRepo.findById(cohortId).orElseThrow();

    var schedules = teachingRepo.findForStudent(semesterId, cohortId, groupNo, shiftTime);
    if (schedules.isEmpty()) {
      return "redirect:/admin/join-codes?semesterId=" + semesterId + "&msg=no_schedule";
    }

    if (scheduleId != null) {
      boolean found = schedules.stream().anyMatch(t -> Objects.equals(t.getId(), scheduleId));
      if (!found) {
        return "redirect:/admin/join-codes?semesterId=" + semesterId + "&msg=bad_subject";
      }
    }

    ClassJoinCode jc = new ClassJoinCode();
    jc.setSemester(sem);
    jc.setCohort(cohort);
    jc.setGroupNo(groupNo);
    jc.setShiftTime(shiftTime);
    jc.setScheduleId(scheduleId);

    jc.setCode(newCode(8));
    jc.setActive(true);
    jc.setExpiresAt(LocalDateTime.now(ZONE).plusDays(14));
    joinRepo.save(jc);

    return "redirect:/admin/join-codes?semesterId=" + semesterId + "&msg=generated";
  }

  @PostMapping("/{id}/close")
  public String close(@PathVariable Long id, @RequestParam Long semesterId) {
    var jc = joinRepo.findById(id).orElseThrow();
    jc.setActive(false);
    joinRepo.save(jc);
    return "redirect:/admin/join-codes?semesterId=" + semesterId + "&msg=closed";
  }

  // ===== API for cascading selects =====
  public record IdLabel(Long id, String label) {
  }

  public record SubjectRow(Long scheduleId, Integer subjectNo, String weekday, String course, String teacher) {
  }

  @GetMapping("/api/cohorts")
  @ResponseBody
  public List<IdLabel> apiCohorts(@RequestParam Long semesterId) {
    var ids = teachingRepo.distinctCohortIds(semesterId);
    var cohorts = cohortRepo.findAllById(ids);
    cohorts.sort(Comparator.comparingInt(c -> c.getCohortNo() != null ? c.getCohortNo() : 9999));
    return cohorts.stream().map(c -> new IdLabel(c.getId(), String.valueOf(c.getCohortNo()))).toList();
  }

  @GetMapping("/api/groups")
  @ResponseBody
  public List<Integer> apiGroups(@RequestParam Long semesterId,
      @RequestParam Long cohortId) {
    return teachingRepo.distinctGroupNos(semesterId, cohortId);
  }

  @GetMapping("/api/shifts")
  @ResponseBody
  public List<String> apiShifts(@RequestParam Long semesterId,
      @RequestParam Long cohortId,
      @RequestParam Integer groupNo) {
    return teachingRepo.distinctShiftTimes(semesterId, cohortId, groupNo)
        .stream().map(Enum::name).toList();
  }

  @GetMapping("/api/subjects")
  @ResponseBody
  public List<SubjectRow> apiSubjects(@RequestParam Long semesterId,
      @RequestParam Long cohortId,
      @RequestParam Integer groupNo,
      @RequestParam ShiftTime shiftTime) {

    var schedules = teachingRepo.findForStudent(semesterId, cohortId, groupNo, shiftTime);
    schedules.sort(Comparator.comparingInt(t -> t.getSubjectNo() != null ? t.getSubjectNo() : 9999));

    return schedules.stream().map(t -> new SubjectRow(
        t.getId(),
        t.getSubjectNo(),
        t.getWeekday() != null ? t.getWeekday().name() : "-",
        t.getCourse() != null ? (t.getCourse().getCode() + " - " + t.getCourse().getName()) : "-",
        t.getTeacher() != null ? t.getTeacher().getUsername() : "-")).toList();
  }

  private static String newCode(int n) {
    StringBuilder sb = new StringBuilder(n);
    for (int i = 0; i < n; i++)
      sb.append(ALPH[RNG.nextInt(ALPH.length)]);
    return sb.toString();
  }
}
