package com.cleanmap.clean_alba_backend.controller;

import com.cleanmap.clean_alba_backend.dto.ReviewResponse;
import com.cleanmap.clean_alba_backend.service.AuthService;
import com.cleanmap.clean_alba_backend.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final ReviewService reviewService;
    private final AuthService authService;

    @GetMapping("/me/reviews")
    public List<ReviewResponse> getMyReviews(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        AuthService.AuthenticatedUser user = authService.authenticate(authorizationHeader);
        return reviewService.getMyReviews(user.email());
    }
}
