package com.collabplatform.dto.auth;

import com.collabplatform.dto.user.UserResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private UserResponse user;
}
