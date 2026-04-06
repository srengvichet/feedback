package kh.edu.num.feedback.api.dto;

public class LoginResponse {
    private String token;
    private String tokenType = "Bearer";
    private long expiresInMs;
    private UserProfileDto user;

    public LoginResponse(String token, long expiresInMs, UserProfileDto user) {
        this.token = token;
        this.expiresInMs = expiresInMs;
        this.user = user;
    }

    public String getToken() { return token; }
    public String getTokenType() { return tokenType; }
    public long getExpiresInMs() { return expiresInMs; }
    public UserProfileDto getUser() { return user; }
}
