package kh.edu.num.feedback.service;

import kh.edu.num.feedback.domain.entity.StudentRegistry;
import kh.edu.num.feedback.domain.entity.UserAccount;
import kh.edu.num.feedback.domain.repo.StudentRegistryRepository;
import kh.edu.num.feedback.domain.repo.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

@Service
public class StudentRegistrySyncService {

  private final StudentRegistryRepository registryRepo;
  private final UserAccountRepository userRepo;

  private static final ZoneId ZONE = ZoneId.of("Asia/Phnom_Penh");

  public StudentRegistrySyncService(StudentRegistryRepository registryRepo,
                                    UserAccountRepository userRepo) {
    this.registryRepo = registryRepo;
    this.userRepo = userRepo;
  }

  /**
   * Sync UserAccount's cohort/group/shift/className from student_registry, and
   * ensure student_registry is linked/claimed by this user (if possible).
   *
   * Safe to call multiple times.
   */
  @Transactional
  public void syncAndClaimIfPresent(UserAccount student) {
    if (student == null || student.getUsername() == null) return;

    StudentRegistry r = registryRepo.findByStudentLoginIgnoreCase(student.getUsername()).orElse(null);
    if (r == null) return;                 // no registry row -> do nothing
    if (!r.isActive()) return;             // registry inactive -> do nothing (or throw)

    // If registry already linked to a DIFFERENT user, do not override.
    if (r.getUser() != null && !Objects.equals(r.getUser().getId(), student.getId())) {
      return; // or throw new IllegalStateException("Registry already claimed by another user");
    }

    boolean changed = false;

    if (r.getCohort() != null && (student.getCohort() == null
        || !Objects.equals(student.getCohort().getId(), r.getCohort().getId()))) {
      student.setCohort(r.getCohort());
      changed = true;
    }

    if (r.getGroupNo() != null && !Objects.equals(student.getGroupNo(), r.getGroupNo())) {
      student.setGroupNo(r.getGroupNo());
      changed = true;
    }

    if (r.getShiftTime() != null && student.getShiftTime() != r.getShiftTime()) {
      student.setShiftTime(r.getShiftTime());
      changed = true;
    }

    // optional, if you still keep className
    if (r.getClassName() != null && (student.getClassName() == null
        || !Objects.equals(student.getClassName(), r.getClassName()))) {
      student.setClassName(r.getClassName());
      changed = true;
    }

    if (changed) {
      userRepo.save(student);
    }

    if (!r.isClaimed() || r.getUser() == null) {
      r.setUser(student);
      r.setClaimed(true);
      r.setClaimedAt(LocalDateTime.now(ZONE));
      registryRepo.save(r);
    }
  }
}
