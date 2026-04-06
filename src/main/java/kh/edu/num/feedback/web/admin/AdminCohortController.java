package kh.edu.num.feedback.web.admin;

import kh.edu.num.feedback.domain.entity.Cohort;
import kh.edu.num.feedback.domain.entity.CohortGroup;
import kh.edu.num.feedback.domain.repo.CohortGroupRepository;
import kh.edu.num.feedback.domain.repo.CohortRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/cohorts")
public class AdminCohortController {

  private final CohortRepository cohortRepo;
  private final CohortGroupRepository cohortGroupRepo;

  public AdminCohortController(CohortRepository cohortRepo, CohortGroupRepository cohortGroupRepo) {
    this.cohortRepo = cohortRepo;
    this.cohortGroupRepo = cohortGroupRepo;
  }

  @GetMapping
  public String list(Model model, @RequestParam(required = false) String msg) {
    List<Cohort> cohorts = cohortRepo.findAllByOrderByCohortNoAsc();

    // groups per cohort (for badge display with delete buttons)
    Map<Long, List<CohortGroup>> groupsMap = new LinkedHashMap<>();
    // groupNos per cohort as String-keyed map (for inline JS serialization)
    Map<String, List<Integer>> cohortGroupNos = new LinkedHashMap<>();

    for (Cohort c : cohorts) {
      List<CohortGroup> groups = cohortGroupRepo.findByCohort_IdOrderByGroupNoAsc(c.getId());
      groupsMap.put(c.getId(), groups);
      cohortGroupNos.put(String.valueOf(c.getId()),
          groups.stream().map(CohortGroup::getGroupNo).collect(Collectors.toList()));
    }

    model.addAttribute("cohorts", cohorts);
    model.addAttribute("groupsMap", groupsMap);
    model.addAttribute("cohortGroupNos", cohortGroupNos);
    model.addAttribute("newCohort", new Cohort());
    model.addAttribute("msg", msg);
    return "admin/cohorts";
  }

  @PostMapping
  public String create(@ModelAttribute("newCohort") Cohort c) {
    if (c.getCohortNo() == null) return "redirect:/admin/cohorts?msg=error";
    if (c.getLabel() != null && c.getLabel().isBlank()) c.setLabel(null);

    try {
      cohortRepo.save(c);
      return "redirect:/admin/cohorts?msg=created";
    } catch (DataIntegrityViolationException ex) {
      return "redirect:/admin/cohorts?msg=duplicate";
    }
  }

  // ── Add a group to a cohort ──────────────────────────────────────────────
  @PostMapping("/{cohortId}/groups")
  public String addGroup(@PathVariable Long cohortId,
                         @RequestParam Integer groupNo) {
    Cohort cohort = cohortRepo.findById(cohortId).orElse(null);
    if (cohort == null) return "redirect:/admin/cohorts?msg=error";

    if (cohortGroupRepo.existsByCohort_IdAndGroupNo(cohortId, groupNo)) {
      return "redirect:/admin/cohorts?msg=group_duplicate";
    }

    CohortGroup g = new CohortGroup();
    g.setCohort(cohort);
    g.setGroupNo(groupNo);
    cohortGroupRepo.save(g);

    return "redirect:/admin/cohorts?msg=group_added";
  }

  // ── Delete a group ───────────────────────────────────────────────────────
  @PostMapping("/{cohortId}/groups/{groupId}/delete")
  public String deleteGroup(@PathVariable Long cohortId,
                            @PathVariable Long groupId) {
    cohortGroupRepo.findById(groupId).ifPresent(cohortGroupRepo::delete);
    return "redirect:/admin/cohorts?msg=group_deleted";
  }
}
