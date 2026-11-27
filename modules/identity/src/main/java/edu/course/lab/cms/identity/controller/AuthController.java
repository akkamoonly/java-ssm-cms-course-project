package edu.course.lab.cms.identity.controller;

import edu.course.lab.cms.identity.dto.LoginRequest;
import edu.course.lab.cms.identity.dto.RegisterRequest;
import edu.course.lab.cms.identity.dto.UserView;
import edu.course.lab.cms.identity.service.AuthService;
import edu.course.lab.cms.identity.session.SessionCurrentUserProvider;
import edu.course.lab.cms.platform.api.ApiException;
import edu.course.lab.cms.platform.api.ApiResponse;
import edu.course.lab.cms.platform.session.CurrentUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final CurrentUserProvider currentUserProvider;

    public AuthController(AuthService auth, CurrentUserProvider currentUserProvider) {
        this.auth = auth;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/register")
    public ApiResponse<UserView> register(@Valid @RequestBody RegisterRequest body) {
        return ApiResponse.success(auth.register(body.username(), body.password(), body.nickname()));
    }

    @PostMapping("/login")
    public ApiResponse<UserView> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        UserView view = auth.login(body.username(), body.password());
        // 确保会话存在后轮换会话 ID 防会话固定，再写入登录态；属性随会话迁移保留。
        var session = request.getSession(true);
        request.changeSessionId();
        session.setAttribute(SessionCurrentUserProvider.SESSION_USER_ID, view.id());
        return ApiResponse.success(view);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ApiResponse.success(null);
    }

    @GetMapping("/me")
    public ApiResponse<UserView> me() {
        var current = currentUserProvider.currentUser()
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "请先登录"));
        return ApiResponse.success(auth.me(current.userId()));
    }
}
