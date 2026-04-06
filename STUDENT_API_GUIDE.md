# NUM Feedback Mobile API – Student Developer Guide
**Version:** 1.0
**Platform:** Flutter / Dart
**Base URL:** `https://your-server.com`
**Authentication:** Bearer JWT Token

---

## Table of Contents
1. [Getting Started](#1-getting-started)
2. [Project Setup (Flutter)](#2-project-setup-flutter)
3. [Authentication](#3-authentication)
4. [Student Endpoints](#4-student-endpoints)
   - [Get Profile](#41-get-profile)
   - [Get My Sections](#42-get-my-sections)
   - [Get Feedback Questions](#43-get-feedback-questions)
   - [Submit Feedback](#44-submit-feedback)
   - [Join Section via QR Code](#45-join-section-via-qr-code)
5. [Dart Model Classes](#5-dart-model-classes)
6. [Full API Service Class](#6-full-api-service-class)
7. [Error Handling](#7-error-handling)
8. [HTTP Status Code Reference](#8-http-status-code-reference)
9. [Notes for Students](#9-notes-for-students)

---

## 1. Getting Started

Your teacher will give you the **Base URL** (the server address).
Replace `https://your-server.com` in all examples with that address.

### Flow Overview

```
1. Login  →  receive JWT token
2. Use token in every request header
3. Get your enrolled sections
4. For each section with windowOpen=true and alreadySubmitted=false → submit feedback
```

---

## 2. Project Setup (Flutter)

### pubspec.yaml dependencies

```yaml
dependencies:
  flutter:
    sdk: flutter
  http: ^1.2.0
  flutter_secure_storage: ^9.0.0
  shared_preferences: ^2.2.2
```

Run:
```bash
flutter pub get
```

### Folder structure (recommended)

```
lib/
  models/
    user_profile.dart
    section.dart
    question.dart
    api_response.dart
  services/
    api_service.dart
    auth_service.dart
  screens/
    login_screen.dart
    sections_screen.dart
    feedback_screen.dart
```

---

## 3. Authentication

### POST `/api/auth/login`

Login with your student username and password to receive a JWT token.

**Request Headers:**
```
Content-Type: application/json
```

**Request Body:**
```json
{
  "username": "student001",
  "password": "yourpassword"
}
```

**Response `200 OK` — Success:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJzdHVkZW50MDAxIiwicm9sZSI6IlNUVURFTlQiLCJpYXQiOjE3MzU2MDAwMDAsImV4cCI6MTczNTY4NjQwMH0.abc123",
  "tokenType": "Bearer",
  "expiresInMs": 86400000,
  "user": {
    "id": 1,
    "username": "student001",
    "fullName": "សុខ ដារ៉ា",
    "email": "dara@num.edu.kh",
    "role": "STUDENT",
    "cohort": "Generation 34",
    "groupNo": 79,
    "className": "15A",
    "shiftTime": "MORNING"
  }
}
```

**Response `401 Unauthorized` — Wrong credentials:**
```json
{
  "success": false,
  "message": "Invalid username or password.",
  "data": null
}
```

**Response `401 Unauthorized` — Account disabled:**
```json
{
  "success": false,
  "message": "Account is disabled.",
  "data": null
}
```

**Flutter Dart code:**
```dart
Future<LoginResponse> login(String username, String password) async {
  final response = await http.post(
    Uri.parse('$baseUrl/api/auth/login'),
    headers: {'Content-Type': 'application/json'},
    body: jsonEncode({
      'username': username,
      'password': password,
    }),
  );

  final json = jsonDecode(response.body);

  if (response.statusCode == 200) {
    return LoginResponse.fromJson(json);
  } else {
    throw ApiException(json['message'] ?? 'Login failed');
  }
}
```

> **Token tip:** The token expires in **24 hours** (`expiresInMs: 86400000`).
> Store it securely using `flutter_secure_storage`. Never use `SharedPreferences` for the token.

---

## 4. Student Endpoints

> All endpoints below **require** this header:
> ```
> Authorization: Bearer <your_token_here>
> ```

---

### 4.1 Get Profile

#### GET `/api/student/profile`

Returns your own student profile information.

**Request Headers:**
```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

**Response `200 OK`:**
```json
{
  "success": true,
  "message": null,
  "data": {
    "id": 1,
    "username": "student001",
    "fullName": "សុខ ដារ៉ា",
    "email": "dara@num.edu.kh",
    "role": "STUDENT",
    "cohort": "Generation 34",
    "groupNo": 79,
    "className": "15A",
    "shiftTime": "MORNING"
  }
}
```

**Flutter Dart code:**
```dart
Future<UserProfile> getProfile() async {
  final response = await http.get(
    Uri.parse('$baseUrl/api/student/profile'),
    headers: _authHeaders(),
  );

  final json = jsonDecode(response.body);
  if (response.statusCode == 200) {
    return UserProfile.fromJson(json['data']);
  } else {
    throw ApiException(json['message'] ?? 'Failed to load profile');
  }
}
```

---

### 4.2 Get My Sections

#### GET `/api/student/sections`

Returns all sections you are enrolled in, with the feedback status for each.

**Request Headers:**
```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

**Response `200 OK`:**
```json
{
  "success": true,
  "message": null,
  "data": [
    {
      "sectionId": 12,
      "semester": "Semester 1 - 2024/2025",
      "courseCode": "CS101",
      "courseName": "Introduction to Programming",
      "teacherName": "ស្រេង វិចិត្រ",
      "shiftTime": "MORNING",
      "room": "A-201",
      "sectionName": "C34-G79-MORNING",
      "windowOpen": true,
      "alreadySubmitted": false,
      "submittedAt": null
    },
    {
      "sectionId": 13,
      "semester": "Semester 1 - 2024/2025",
      "courseCode": "MATH201",
      "courseName": "Calculus",
      "teacherName": "ចាន់ សុភា",
      "shiftTime": "MORNING",
      "room": "B-102",
      "sectionName": "C34-G79-MORNING",
      "windowOpen": true,
      "alreadySubmitted": true,
      "submittedAt": "2024-11-15T10:30:00"
    },
    {
      "sectionId": 14,
      "semester": "Semester 1 - 2024/2025",
      "courseCode": "ENG301",
      "courseName": "Technical Writing",
      "teacherName": "លី សុខហេង",
      "shiftTime": "MORNING",
      "room": "C-305",
      "sectionName": "C34-G79-MORNING",
      "windowOpen": false,
      "alreadySubmitted": false,
      "submittedAt": null
    }
  ]
}
```

**Field explanation:**

| Field | Type | Description |
|-------|------|-------------|
| `sectionId` | number | Unique ID for the section. Use this in other API calls. |
| `semester` | string | Semester name |
| `courseCode` | string | Course code (e.g. `CS101`) |
| `courseName` | string | Full course name |
| `teacherName` | string | Teacher's name |
| `shiftTime` | string | `MORNING`, `EARLY_AFTERNOON`, `AFTERNOON`, or `EVENING` |
| `room` | string | Building and room (e.g. `A-201`) |
| `sectionName` | string | Section name |
| `windowOpen` | boolean | `true` = the feedback window is currently open |
| `alreadySubmitted` | boolean | `true` = you have already submitted feedback for this section |
| `submittedAt` | string or null | ISO datetime of your submission, or `null` if not submitted |

**Understanding the status combinations:**

| `windowOpen` | `alreadySubmitted` | What to show in your app |
|---|---|---|
| `true` | `false` | Show **"Give Feedback"** button — student can submit |
| `true` | `true` | Show **"Submitted"** status with date — already done |
| `false` | `false` | Show **"Closed"** — window not open yet (or already closed) |
| `false` | `true` | Show **"Submitted"** status — done before window closed |

**Flutter Dart code:**
```dart
Future<List<Section>> getSections() async {
  final response = await http.get(
    Uri.parse('$baseUrl/api/student/sections'),
    headers: _authHeaders(),
  );

  final json = jsonDecode(response.body);
  if (response.statusCode == 200) {
    final List<dynamic> data = json['data'];
    return data.map((item) => Section.fromJson(item)).toList();
  } else {
    throw ApiException(json['message'] ?? 'Failed to load sections');
  }
}
```

---

### 4.3 Get Feedback Questions

#### GET `/api/student/feedback/{sectionId}/questions`

Returns the list of questions to answer for a specific section.

> Only works when `windowOpen = true` AND `alreadySubmitted = false`.

**Path Parameter:**
- `{sectionId}` — the `sectionId` from the sections list (e.g. `12`)

**Full URL example:**
```
GET /api/student/feedback/12/questions
```

**Request Headers:**
```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

**Response `200 OK`:**
```json
{
  "success": true,
  "message": null,
  "data": [
    {
      "id": 1,
      "orderNo": 1,
      "text": "គ្រូបង្រៀនមានចំណេះដឹងគ្រប់គ្រាន់លើមុខវិជ្ជា",
      "type": "RATING",
      "scaleMin": 1,
      "scaleMax": 5
    },
    {
      "id": 2,
      "orderNo": 2,
      "text": "គ្រូបង្រៀនផ្ដល់ការពន្យល់ច្បាស់លាស់",
      "type": "RATING",
      "scaleMin": 1,
      "scaleMax": 5
    },
    {
      "id": 27,
      "orderNo": 27,
      "text": "មតិយោបល់បន្ថែម",
      "type": "TEXT",
      "scaleMin": null,
      "scaleMax": null
    }
  ]
}
```

**Question types:**

| `type` | Description | How to display | Expected answer |
|--------|-------------|----------------|-----------------|
| `RATING` | Numeric rating | Stars or slider from `scaleMin` to `scaleMax` | Integer as string, e.g. `"4"` |
| `TEXT` | Open comment | Multi-line text field | Any text string |

**Error responses:**
- `403 Forbidden` — You are not enrolled, or the feedback window is closed
- `409 Conflict` — You have already submitted feedback for this section

**Flutter Dart code:**
```dart
Future<List<Question>> getQuestions(int sectionId) async {
  final response = await http.get(
    Uri.parse('$baseUrl/api/student/feedback/$sectionId/questions'),
    headers: _authHeaders(),
  );

  final json = jsonDecode(response.body);

  if (response.statusCode == 200) {
    final List<dynamic> data = json['data'];
    return data.map((item) => Question.fromJson(item)).toList();
  } else if (response.statusCode == 409) {
    throw AlreadySubmittedException();
  } else {
    throw ApiException(json['message'] ?? 'Failed to load questions');
  }
}
```

---

### 4.4 Submit Feedback

#### POST `/api/student/feedback/{sectionId}`

Submit your answers for a section.

**Path Parameter:**
- `{sectionId}` — the section ID (e.g. `12`)

**Full URL example:**
```
POST /api/student/feedback/12
```

**Request Headers:**
```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
Content-Type: application/json
```

**Request Body:**
```json
{
  "answers": {
    "1": "5",
    "2": "4",
    "3": "5",
    "4": "3",
    "5": "4",
    "27": "The teacher is very helpful and explains clearly."
  }
}
```

> **Important:**
> - The keys are **question IDs** (as strings, matching the `id` field from the questions list)
> - `RATING` answers must be a number string within the allowed scale (e.g. `"1"` to `"5"`)
> - `TEXT` answers are any string

**Response `200 OK` — Success:**
```json
{
  "success": true,
  "message": "Feedback submitted successfully.",
  "data": null
}
```

**Error responses:**
- `403 Forbidden` — Not enrolled, or feedback window is closed
- `404 Not Found` — Section not found

**Flutter Dart code:**
```dart
Future<void> submitFeedback(int sectionId, Map<String, String> answers) async {
  final response = await http.post(
    Uri.parse('$baseUrl/api/student/feedback/$sectionId'),
    headers: {
      ..._authHeaders(),
      'Content-Type': 'application/json',
    },
    body: jsonEncode({'answers': answers}),
  );

  if (response.statusCode != 200) {
    final json = jsonDecode(response.body);
    throw ApiException(json['message'] ?? 'Failed to submit feedback');
  }
}
```

**How to build the answers map from a form:**
```dart
// Example: user rated question 1 as 5, question 2 as 4, typed comment for question 27
Map<String, String> answers = {
  '1': '5',       // RATING question
  '2': '4',       // RATING question
  '27': userComment,  // TEXT question
};

await apiService.submitFeedback(sectionId, answers);
```

---

### 4.5 Join Section via QR Code

#### POST `/api/student/join/{code}`

Validate a QR join code. This verifies the code is valid and matches your Cohort/Group/Shift.

**Path Parameter:**
- `{code}` — the join code from the QR code (e.g. `ABC123`)

**Full URL example:**
```
POST /api/student/join/ABC123
```

**Request Headers:**
```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

**Response `200 OK` — Code is valid:**
```json
{
  "success": true,
  "message": "Joined section successfully. Please use the web portal to complete enrollment.",
  "data": null
}
```

**Response `400 Bad Request` — Code invalid or expired:**
```json
{
  "success": false,
  "message": "Join code is invalid, closed, or expired.",
  "data": null
}
```

**Response `403 Forbidden` — Code is for different cohort/group/shift:**
```json
{
  "success": false,
  "message": "This join code is not for your Cohort/Group/Shift.",
  "data": null
}
```

**Flutter Dart code:**
```dart
Future<String> joinSection(String code) async {
  final response = await http.post(
    Uri.parse('$baseUrl/api/student/join/${code.trim().toUpperCase()}'),
    headers: _authHeaders(),
  );

  final json = jsonDecode(response.body);

  if (response.statusCode == 200) {
    return json['message'];
  } else {
    throw ApiException(json['message'] ?? 'Failed to join section');
  }
}
```

---

## 5. Dart Model Classes

Create these files in `lib/models/`:

### `lib/models/api_response.dart`
```dart
class ApiException implements Exception {
  final String message;
  ApiException(this.message);

  @override
  String toString() => message;
}

class AlreadySubmittedException implements Exception {}
```

### `lib/models/user_profile.dart`
```dart
class UserProfile {
  final int id;
  final String username;
  final String fullName;
  final String? email;
  final String role;
  final String? cohort;
  final int? groupNo;
  final String? className;
  final String? shiftTime;

  UserProfile({
    required this.id,
    required this.username,
    required this.fullName,
    this.email,
    required this.role,
    this.cohort,
    this.groupNo,
    this.className,
    this.shiftTime,
  });

  factory UserProfile.fromJson(Map<String, dynamic> json) {
    return UserProfile(
      id: json['id'],
      username: json['username'],
      fullName: json['fullName'],
      email: json['email'],
      role: json['role'],
      cohort: json['cohort'],
      groupNo: json['groupNo'],
      className: json['className'],
      shiftTime: json['shiftTime'],
    );
  }
}
```

### `lib/models/login_response.dart`
```dart
import 'user_profile.dart';

class LoginResponse {
  final String token;
  final String tokenType;
  final int expiresInMs;
  final UserProfile user;

  LoginResponse({
    required this.token,
    required this.tokenType,
    required this.expiresInMs,
    required this.user,
  });

  factory LoginResponse.fromJson(Map<String, dynamic> json) {
    return LoginResponse(
      token: json['token'],
      tokenType: json['tokenType'],
      expiresInMs: json['expiresInMs'],
      user: UserProfile.fromJson(json['user']),
    );
  }
}
```

### `lib/models/section.dart`
```dart
class Section {
  final int sectionId;
  final String semester;
  final String courseCode;
  final String courseName;
  final String teacherName;
  final String shiftTime;
  final String room;
  final String sectionName;
  final bool windowOpen;
  final bool alreadySubmitted;
  final DateTime? submittedAt;

  Section({
    required this.sectionId,
    required this.semester,
    required this.courseCode,
    required this.courseName,
    required this.teacherName,
    required this.shiftTime,
    required this.room,
    required this.sectionName,
    required this.windowOpen,
    required this.alreadySubmitted,
    this.submittedAt,
  });

  factory Section.fromJson(Map<String, dynamic> json) {
    return Section(
      sectionId: json['sectionId'],
      semester: json['semester'] ?? '',
      courseCode: json['courseCode'] ?? '',
      courseName: json['courseName'] ?? '',
      teacherName: json['teacherName'] ?? '',
      shiftTime: json['shiftTime'] ?? '',
      room: json['room'] ?? '',
      sectionName: json['sectionName'] ?? '',
      windowOpen: json['windowOpen'] ?? false,
      alreadySubmitted: json['alreadySubmitted'] ?? false,
      submittedAt: json['submittedAt'] != null
          ? DateTime.parse(json['submittedAt'])
          : null,
    );
  }

  /// Returns true if the student can submit feedback now
  bool get canSubmit => windowOpen && !alreadySubmitted;

  String get statusLabel {
    if (alreadySubmitted) return 'Submitted';
    if (windowOpen) return 'Pending';
    return 'Closed';
  }
}
```

### `lib/models/question.dart`
```dart
class Question {
  final int id;
  final int orderNo;
  final String text;
  final String type; // "RATING" or "TEXT"
  final int? scaleMin;
  final int? scaleMax;

  Question({
    required this.id,
    required this.orderNo,
    required this.text,
    required this.type,
    this.scaleMin,
    this.scaleMax,
  });

  bool get isRating => type == 'RATING';
  bool get isText => type == 'TEXT';

  factory Question.fromJson(Map<String, dynamic> json) {
    return Question(
      id: json['id'],
      orderNo: json['orderNo'],
      text: json['text'],
      type: json['type'],
      scaleMin: json['scaleMin'],
      scaleMax: json['scaleMax'],
    );
  }
}
```

---

## 6. Full API Service Class

Create `lib/services/api_service.dart`:

```dart
import 'dart:convert';
import 'package:http/http.dart' as http;
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import '../models/api_response.dart';
import '../models/login_response.dart';
import '../models/user_profile.dart';
import '../models/section.dart';
import '../models/question.dart';

class ApiService {
  static const String baseUrl = 'https://your-server.com'; // <-- Change this!
  static const _storage = FlutterSecureStorage();
  static const _tokenKey = 'auth_token';

  // ─── Token Management ───────────────────────────────────────────────

  Future<void> saveToken(String token) async {
    await _storage.write(key: _tokenKey, value: token);
  }

  Future<String?> getToken() async {
    return await _storage.read(key: _tokenKey);
  }

  Future<void> deleteToken() async {
    await _storage.delete(key: _tokenKey);
  }

  Future<bool> isLoggedIn() async {
    final token = await getToken();
    return token != null && token.isNotEmpty;
  }

  Map<String, String> _authHeaders(String token) => {
    'Authorization': 'Bearer $token',
    'Content-Type': 'application/json',
  };

  // ─── Authentication ──────────────────────────────────────────────────

  Future<LoginResponse> login(String username, String password) async {
    final response = await http.post(
      Uri.parse('$baseUrl/api/auth/login'),
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode({'username': username, 'password': password}),
    );

    final json = jsonDecode(response.body);

    if (response.statusCode == 200) {
      final loginResponse = LoginResponse.fromJson(json);
      await saveToken(loginResponse.token);
      return loginResponse;
    } else {
      throw ApiException(json['message'] ?? 'Login failed');
    }
  }

  Future<void> logout() async {
    await deleteToken();
  }

  // ─── Student Profile ─────────────────────────────────────────────────

  Future<UserProfile> getProfile() async {
    final token = await getToken();
    if (token == null) throw ApiException('Not logged in');

    final response = await http.get(
      Uri.parse('$baseUrl/api/student/profile'),
      headers: _authHeaders(token),
    );

    final json = jsonDecode(response.body);

    if (response.statusCode == 200) {
      return UserProfile.fromJson(json['data']);
    } else if (response.statusCode == 401) {
      await deleteToken(); // Token expired
      throw ApiException('Session expired. Please log in again.');
    } else {
      throw ApiException(json['message'] ?? 'Failed to load profile');
    }
  }

  // ─── Sections ────────────────────────────────────────────────────────

  Future<List<Section>> getSections() async {
    final token = await getToken();
    if (token == null) throw ApiException('Not logged in');

    final response = await http.get(
      Uri.parse('$baseUrl/api/student/sections'),
      headers: _authHeaders(token),
    );

    final json = jsonDecode(response.body);

    if (response.statusCode == 200) {
      final List<dynamic> data = json['data'];
      return data.map((item) => Section.fromJson(item)).toList();
    } else if (response.statusCode == 401) {
      await deleteToken();
      throw ApiException('Session expired. Please log in again.');
    } else {
      throw ApiException(json['message'] ?? 'Failed to load sections');
    }
  }

  // ─── Feedback Questions ──────────────────────────────────────────────

  Future<List<Question>> getQuestions(int sectionId) async {
    final token = await getToken();
    if (token == null) throw ApiException('Not logged in');

    final response = await http.get(
      Uri.parse('$baseUrl/api/student/feedback/$sectionId/questions'),
      headers: _authHeaders(token),
    );

    final json = jsonDecode(response.body);

    if (response.statusCode == 200) {
      final List<dynamic> data = json['data'];
      return data.map((item) => Question.fromJson(item)).toList();
    } else if (response.statusCode == 409) {
      throw AlreadySubmittedException();
    } else if (response.statusCode == 401) {
      await deleteToken();
      throw ApiException('Session expired. Please log in again.');
    } else {
      throw ApiException(json['message'] ?? 'Failed to load questions');
    }
  }

  // ─── Submit Feedback ─────────────────────────────────────────────────

  Future<void> submitFeedback(int sectionId, Map<String, String> answers) async {
    final token = await getToken();
    if (token == null) throw ApiException('Not logged in');

    final response = await http.post(
      Uri.parse('$baseUrl/api/student/feedback/$sectionId'),
      headers: _authHeaders(token),
      body: jsonEncode({'answers': answers}),
    );

    if (response.statusCode == 200) return;

    final json = jsonDecode(response.body);
    if (response.statusCode == 401) {
      await deleteToken();
      throw ApiException('Session expired. Please log in again.');
    }
    throw ApiException(json['message'] ?? 'Failed to submit feedback');
  }

  // ─── QR Code Join ────────────────────────────────────────────────────

  Future<String> joinSection(String code) async {
    final token = await getToken();
    if (token == null) throw ApiException('Not logged in');

    final cleanCode = code.trim().toUpperCase();

    final response = await http.post(
      Uri.parse('$baseUrl/api/student/join/$cleanCode'),
      headers: _authHeaders(token),
    );

    final json = jsonDecode(response.body);

    if (response.statusCode == 200) {
      return json['message'];
    } else if (response.statusCode == 401) {
      await deleteToken();
      throw ApiException('Session expired. Please log in again.');
    } else {
      throw ApiException(json['message'] ?? 'Failed to join section');
    }
  }
}
```

---

## 7. Error Handling

### Always handle these cases in your UI:

```dart
try {
  final sections = await apiService.getSections();
  // update UI with sections
} on ApiException catch (e) {
  if (e.message.contains('Session expired')) {
    // Navigate to login screen
    Navigator.pushReplacementNamed(context, '/login');
  } else {
    // Show error message
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(e.message)),
    );
  }
} catch (e) {
  // Network error (no internet, server down)
  ScaffoldMessenger.of(context).showSnackBar(
    const SnackBar(content: Text('Network error. Please check your connection.')),
  );
}
```

### Token expired — auto-redirect to login:

```dart
// In your ApiService, when you get 401:
// 1. Delete the stored token
// 2. Throw ApiException('Session expired. Please log in again.')
// 3. In your UI catch block, navigate to login screen
```

---

## 8. HTTP Status Code Reference

| Status Code | Meaning | What to do in Flutter |
|---|---|---|
| `200` | Success | Read `data` field |
| `400` | Bad request (invalid input) | Show `message` to user |
| `401` | Not authenticated / token expired | Delete token, go to login screen |
| `403` | Forbidden (not enrolled, window closed, wrong cohort) | Show `message` to user |
| `404` | Section not found | Show error message |
| `409` | Already submitted | Show "Already submitted" message |

---

## 9. Notes for Students

### Token Security
- **DO** use `flutter_secure_storage` to save the token
- **DO NOT** use `SharedPreferences` for the token (not secure)
- **DO NOT** hardcode your token in the code
- The token expires after **24 hours** — when you get a `401` response, delete the token and send the user back to the login screen

### Feedback Submission Rules
- You can only submit feedback when `windowOpen = true` **AND** `alreadySubmitted = false`
- Once submitted, you **cannot** change or resubmit — check before submitting
- All RATING answers must be integers within the `scaleMin` to `scaleMax` range
- TEXT answers can be left empty (empty string `""`) if the student has no comment

### Shift Time Values

| Value | Meaning |
|---|---|
| `MORNING` | Morning shift |
| `EARLY_AFTERNOON` | Early afternoon shift |
| `AFTERNOON` | Afternoon shift |
| `EVENING` | Evening shift |

### Testing Tips
1. Login first and copy the token from the response
2. Use [Postman](https://www.postman.com/) or [Hoppscotch](https://hoppscotch.io/) to test each endpoint manually before coding
3. Always check `windowOpen` and `alreadySubmitted` before navigating to the feedback form screen
4. Test with a section that has `windowOpen = true` and `alreadySubmitted = false` to test the full submit flow

---

*Generated for NUM Feedback System — Student Mobile API v1.0*
