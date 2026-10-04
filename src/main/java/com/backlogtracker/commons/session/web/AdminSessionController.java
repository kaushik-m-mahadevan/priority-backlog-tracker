package com.backlogtracker.commons.session.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.backlogtracker.commons.security.RequiresAdmin;
import com.backlogtracker.commons.session.dto.SessionPolicyView;
import com.backlogtracker.commons.session.dto.SessionView;
import com.backlogtracker.commons.session.service.SessionService;

import lombok.RequiredArgsConstructor;

/** Admin view of who logged in and out when, plus the inactivity timeout. */
@RestController
@RequestMapping("/api/admin")
@RequiresAdmin
@RequiredArgsConstructor
public class AdminSessionController {

    private final SessionService sessionService;

    /** Newest first; optionally one user's history. */
    @GetMapping("/sessions")
    public List<SessionView> sessions(@RequestParam(required = false) String userId,
                                      @RequestParam(defaultValue = "200") int limit) {
        return sessionService.recent(userId, limit).stream().map(SessionView::of).toList();
    }

    @GetMapping("/session-policy")
    public SessionPolicyView policy() {
        return new SessionPolicyView((int) sessionService.timeout().toMinutes());
    }

    @PutMapping("/session-policy")
    public SessionPolicyView setPolicy(@RequestBody SessionPolicyView body) {
        sessionService.setTimeoutMinutes(body.timeoutMinutes());
        return policy();
    }
}
