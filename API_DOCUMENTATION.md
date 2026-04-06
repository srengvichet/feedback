# NUM Feedback – Mobile API Documentation
**Base URL:** `https://your-server.com`
**Version:** 1.0
**Format:** JSON
**Authentication:** Bearer JWT Token

---

## Authentication

### POST `/api/auth/login`
Login and receive a JWT token.

**Request Body:**
```json
{
  "username": "student001",
  "password": "yourpassword"
}
```

**Response `200 OK`:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
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

**Response `401 Unauthorized`:**
```json
{
  "success": false,
  "message": "Invalid username or password.",
  "data": null
}
```

**How to use the token in Flutter:**
```dart
final response = await http.get(
  Uri.parse('$baseUrl/api/student/sections'),
  headers: {
    'Authorization': 'Bearer $token',
    'Content-Type': 'application/json',
  },
);
```

---

## Student Endpoints

> All endpoints below require the `Authorization: Bearer <token>` header.

---

### GET `/api/student/profile`
Get the logged-in student's profile.

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

---

### GET `/api/student/sections`
Get all sections the student is enrolled in, with feedback status.

**Response `200 OK`:**
```json
{
  "success": true,
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
    }
  ]
}
```

**Field Guide:**
| Field | Description |
|-------|-------------|
| `windowOpen` | `true` = feedback window is open, student can submit |
| `alreadySubmitted` | `true` = student already submitted for this section |
| `submittedAt` | Date/time of submission, `null` if not yet submitted |

---

### GET `/api/student/feedback/{sectionId}/questions`
Get the list of feedback questions for a section.

> Only available when `windowOpen = true` and `alreadySubmitted = false`.

**Path Parameter:** `sectionId` — the section ID from `/api/student/sections`

**Response `200 OK`:**
```json
{
  "success": true,
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

**Question Types:**
| Type | Description | Expected Answer |
|------|-------------|-----------------|
| `RATING` | Numeric rating question | Integer between `scaleMin` and `scaleMax` |
| `TEXT` | Open-ended comment | Any string |

**Error Responses:**
- `403 Forbidden` — Not enrolled, or window is closed
- `409 Conflict` — Already submitted

---

### POST `/api/student/feedback/{sectionId}`
Submit feedback answers for a section.

**Path Parameter:** `sectionId` — the section ID

**Request Body:**
```json
{
  "answers": {
    "1": "5",
    "2": "4",
    "3": "5",
    "4": "4",
    "5": "5",
    "27": "The teacher explains clearly and is very helpful."
  }
}
```

> Keys are question IDs (as strings), values are the answer values.
> RATING answers must be integers within the allowed scale.
> TEXT answers are free-form strings.

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Feedback submitted successfully.",
  "data": null
}
```

**Error Responses:**
- `403 Forbidden` — Not enrolled, or window is closed
- `404 Not Found` — Section not found

---

### POST `/api/student/join/{code}`
Validate a QR join code. Used to verify a code before prompting the student to confirm enrollment.

**Path Parameter:** `code` — the join code (e.g. `ABC123`)

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Joined section successfully. Please use the web portal to complete enrollment.",
  "data": null
}
```

**Error Responses:**
- `400 Bad Request` — Code is invalid, expired, or inactive
- `403 Forbidden` — Code is for a different cohort/group/shift

---

## Error Response Format

All errors follow this structure:
```json
{
  "success": false,
  "message": "Human-readable error message.",
  "data": null
}
```

**HTTP Status Codes:**
| Code | Meaning |
|------|---------|
| `200` | Success |
| `400` | Bad request (missing/invalid input) |
| `401` | Not authenticated (missing or invalid token) |
| `403` | Forbidden (enrolled check, window closed, wrong cohort) |
| `404` | Resource not found |
| `409` | Conflict (already submitted) |

---

## Shift Time Values

| Value | Meaning |
|-------|---------|
| `MORNING` | Morning shift |
| `EARLY_AFTERNOON` | Early afternoon shift |
| `AFTERNOON` | Afternoon shift |
| `EVENING` | Evening shift |

---

## Flutter Integration Example

```dart
class FeedbackApiService {
  static const String baseUrl = 'https://your-server.com';
  String? _token;

  // Login
  Future<void> login(String username, String password) async {
    final res = await http.post(
      Uri.parse('$baseUrl/api/auth/login'),
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode({'username': username, 'password': password}),
    );
    if (res.statusCode == 200) {
      _token = jsonDecode(res.body)['token'];
    } else {
      throw Exception(jsonDecode(res.body)['message']);
    }
  }

  // Get sections
  Future<List<dynamic>> getSections() async {
    final res = await http.get(
      Uri.parse('$baseUrl/api/student/sections'),
      headers: {'Authorization': 'Bearer $_token'},
    );
    return jsonDecode(res.body)['data'];
  }

  // Get questions
  Future<List<dynamic>> getQuestions(int sectionId) async {
    final res = await http.get(
      Uri.parse('$baseUrl/api/student/feedback/$sectionId/questions'),
      headers: {'Authorization': 'Bearer $_token'},
    );
    if (res.statusCode == 200) return jsonDecode(res.body)['data'];
    throw Exception(jsonDecode(res.body)['message']);
  }

  // Submit feedback
  Future<void> submitFeedback(int sectionId, Map<String, String> answers) async {
    final res = await http.post(
      Uri.parse('$baseUrl/api/student/feedback/$sectionId'),
      headers: {
        'Authorization': 'Bearer $_token',
        'Content-Type': 'application/json',
      },
      body: jsonEncode({'answers': answers}),
    );
    if (res.statusCode != 200) {
      throw Exception(jsonDecode(res.body)['message']);
    }
  }
}
```

---

## JWT Token Notes

- Token expires after **24 hours** (`expiresInMs: 86400000`)
- Store the token securely using `flutter_secure_storage`
- If a request returns `401`, the token has expired — redirect the user to the login screen
- Include the token in every request header: `Authorization: Bearer <token>`
