package kh.edu.num.feedback.init;



import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import kh.edu.num.feedback.domain.entity.EvaluationKind;
import kh.edu.num.feedback.domain.entity.Question;
import kh.edu.num.feedback.domain.entity.QuestionType;
import kh.edu.num.feedback.domain.entity.Role;
import kh.edu.num.feedback.domain.entity.UserAccount;
import kh.edu.num.feedback.domain.repo.QuestionRepository;
import kh.edu.num.feedback.domain.repo.UserAccountRepository;


@Component
public class DataSeeder implements CommandLineRunner {

  private final UserAccountRepository repo;
  private final PasswordEncoder encoder;
  // add field
  private final QuestionRepository questionRepo;

 // update constructor
  public DataSeeder(UserAccountRepository repo, PasswordEncoder encoder, QuestionRepository questionRepo) {
    this.repo = repo;
    this.encoder = encoder;
    this.questionRepo = questionRepo;
  }

  @Override
  public void run(String... args) {
    seed("admin",  "admin123",  Role.ADMIN);
    seed("qa",     "qa123",     Role.QA);
    seed("teacher","teacher123",Role.TEACHER);
    seed("student","student123",Role.STUDENT);
    seedDefaultQuestions();
  }

  private void seed(String username, String rawPassword, Role role) {
    if (repo.existsByUsername(username)) return;

    UserAccount u = new UserAccount();
    u.setUsername(username);
    u.setPasswordHash(encoder.encode(rawPassword));
    u.setRole(role);
    u.setEnabled(true);
    repo.save(u);
  }
  private void seedDefaultQuestions() {
  if (questionRepo.count() > 0) return;

  // Student feedback (5 ratings + 1 comment)
  addQ(EvaluationKind.STUDENT_FEEDBACK, QuestionType.RATING, "The teacher explains clearly.", 1, 5, 1);
  addQ(EvaluationKind.STUDENT_FEEDBACK, QuestionType.RATING, "The teacher is well-prepared for the class.", 1, 5, 2);
  addQ(EvaluationKind.STUDENT_FEEDBACK, QuestionType.RATING, "The teacher is fair in assessment and grading.", 1, 5, 3);
  addQ(EvaluationKind.STUDENT_FEEDBACK, QuestionType.RATING, "The teacher encourages questions and participation.", 1, 5, 4);
  addQ(EvaluationKind.STUDENT_FEEDBACK, QuestionType.RATING, "Overall satisfaction with this course.", 1, 5, 5);
  addQ(EvaluationKind.STUDENT_FEEDBACK, QuestionType.TEXT,   "Comments / suggestions (optional).", null, null, 6);

  // Teacher self-assessment (4 ratings + 1 comment)
  addQ(EvaluationKind.TEACHER_SELF, QuestionType.RATING, "I prepared lesson plans and materials on time.", 1, 5, 1);
  addQ(EvaluationKind.TEACHER_SELF, QuestionType.RATING, "I managed class time effectively.", 1, 5, 2);
  addQ(EvaluationKind.TEACHER_SELF, QuestionType.RATING, "I used appropriate teaching methods and activities.", 1, 5, 3);
  addQ(EvaluationKind.TEACHER_SELF, QuestionType.RATING, "I provided timely feedback to students.", 1, 5, 4);
  addQ(EvaluationKind.TEACHER_SELF, QuestionType.TEXT,   "Self-reflection / improvement plan (optional).", null, null, 5);
  }
  private void addQ(EvaluationKind kind, QuestionType type, String text,
                    Integer min, Integer max, int orderNo) {
    Question q = new Question();
    q.setKind(kind);
    q.setType(type);
    q.setText(text);
    q.setScaleMin(min);
    q.setScaleMax(max);
    q.setOrderNo(orderNo);
    q.setActive(true);
    questionRepo.save(q);
  }
}

